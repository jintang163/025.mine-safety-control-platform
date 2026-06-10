package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mine.safety.domain.Sensor;
import com.mine.safety.domain.SensorData;
import com.mine.safety.dto.SensorDTO;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.repository.SensorDataRepository;
import com.mine.safety.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 传感器管理服务
 * 提供传感器的CRUD管理、最新数据查询、历史数据查询等核心功能。
 * 离线检测已统一由Quartz定时任务(SensorOfflineCheckJob)负责。
 *
 * 数据查询优先级：
 *   1. Redis缓存（最快，5分钟过期）
 *   2. MySQL最新记录（次快，最新1条）
 *   3. 内存缓存（兜底，程序启动后接收的数据）
 *
 * @author mine-safety
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorService {

    /** 传感器配置Repository */
    private final SensorRepository sensorRepository;

    /** 传感器数据Repository（MySQL历史数据） */
    private final SensorDataRepository sensorDataRepository;

    /** Redis模板，用于实时数据缓存 */
    private final StringRedisTemplate redisTemplate;

    /** InfluxDB服务，用于时序数据查询 */
    private final InfluxDBService influxDBService;

    /**
     * 内存缓存：存储每个传感器的最新数据
     * 作为Redis和MySQL查询失败时的兜底方案
     */
    private final Map<String, SensorDataDTO> latestDataCache = new ConcurrentHashMap<>();

    /**
     * 查询所有传感器列表
     *
     * @return 所有传感器DTO列表
     */
    public List<SensorDTO> getAllSensors() {
        return sensorRepository.selectList(null).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按传感器类型查询
     *
     * @param type 传感器类型（GAS/DUST/CO等）
     * @return 该类型的传感器列表
     */
    public List<SensorDTO> getSensorsByType(String type) {
        return sensorRepository.selectList(new LambdaQueryWrapper<Sensor>().eq(Sensor::getType, type)).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按状态查询传感器
     *
     * @param status 状态（0-离线，1-在线，2-故障）
     * @return 该状态的传感器列表
     */
    public List<SensorDTO> getSensorsByStatus(Integer status) {
        return sensorRepository.selectList(new LambdaQueryWrapper<Sensor>().eq(Sensor::getStatus, status)).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按区域编码查询传感器
     *
     * @param zoneCode 区域编码
     * @return 该区域的传感器列表
     */
    public List<SensorDTO> getSensorsByZone(String zoneCode) {
        return sensorRepository.selectList(new LambdaQueryWrapper<Sensor>().eq(Sensor::getZoneCode, zoneCode)).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据传感器ID查询详情
     *
     * @param sensorId 传感器ID
     * @return 传感器DTO
     * @throws RuntimeException 传感器不存在时抛出
     */
    public SensorDTO getSensorById(String sensorId) {
        Sensor sensor = sensorRepository.selectOne(
                new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, sensorId));
        if (sensor == null) {
            throw new RuntimeException("传感器不存在: " + sensorId);
        }
        return convertToDTO(sensor);
    }

    /**
     * 创建新传感器
     *
     * @param dto 传感器信息
     * @return 创建后的传感器DTO
     * @throws RuntimeException 传感器ID已存在时抛出
     */
    @Transactional
    public SensorDTO createSensor(SensorDTO dto) {
        if (sensorRepository.selectCount(new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, dto.getSensorId())) > 0) {
            throw new RuntimeException("传感器ID已存在: " + dto.getSensorId());
        }

        Sensor sensor = new Sensor();
        sensor.setSensorId(dto.getSensorId());
        sensor.setName(dto.getName());
        sensor.setType(dto.getType());
        sensor.setProtocol(dto.getProtocol());
        sensor.setLocation(dto.getLocation());
        sensor.setCoordinatesX(dto.getCoordinatesX());
        sensor.setCoordinatesY(dto.getCoordinatesY());
        sensor.setCoordinatesZ(dto.getCoordinatesZ());
        sensor.setSamplingInterval(dto.getSamplingInterval() != null ? dto.getSamplingInterval() : 1);
        sensor.setMinValue(dto.getMinValue());
        sensor.setMaxValue(dto.getMaxValue());
        sensor.setUnit(dto.getUnit());
        sensor.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        sensor.setWarningThreshold(dto.getWarningThreshold());
        sensor.setAlarmThreshold(dto.getAlarmThreshold());
        sensor.setPowerOffThreshold(dto.getPowerOffThreshold());
        sensor.setZoneCode(dto.getZoneCode());
        sensor.setBatteryLevel(dto.getBatteryLevel() != null ? dto.getBatteryLevel() : 100);
        sensor.setSignalStrength(dto.getSignalStrength() != null ? dto.getSignalStrength() : 0);
        sensor.setDataUploadDelay(dto.getDataUploadDelay());
        sensor.setOfflineTimeoutMinutes(dto.getOfflineTimeoutMinutes() != null ? dto.getOfflineTimeoutMinutes() : 10);
        sensor.setCalibrationCycleDays(dto.getCalibrationCycleDays());

        sensor = sensorRepository.insert(sensor);
        return convertToDTO(sensor);
    }

    /**
     * 更新传感器信息
     * 支持部分字段更新（仅更新非空字段）
     *
     * @param sensorId 传感器ID
     * @param dto      更新的传感器信息
     * @return 更新后的传感器DTO
     * @throws RuntimeException 传感器不存在时抛出
     */
    @Transactional
    public SensorDTO updateSensor(String sensorId, SensorDTO dto) {
        Sensor sensor = sensorRepository.selectOne(
                new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, sensorId));
        if (sensor == null) {
            throw new RuntimeException("传感器不存在: " + sensorId);
        }

        if (dto.getName() != null) sensor.setName(dto.getName());
        if (dto.getType() != null) sensor.setType(dto.getType());
        if (dto.getProtocol() != null) sensor.setProtocol(dto.getProtocol());
        if (dto.getLocation() != null) sensor.setLocation(dto.getLocation());
        if (dto.getCoordinatesX() != null) sensor.setCoordinatesX(dto.getCoordinatesX());
        if (dto.getCoordinatesY() != null) sensor.setCoordinatesY(dto.getCoordinatesY());
        if (dto.getCoordinatesZ() != null) sensor.setCoordinatesZ(dto.getCoordinatesZ());
        if (dto.getSamplingInterval() != null) sensor.setSamplingInterval(dto.getSamplingInterval());
        if (dto.getMinValue() != null) sensor.setMinValue(dto.getMinValue());
        if (dto.getMaxValue() != null) sensor.setMaxValue(dto.getMaxValue());
        if (dto.getUnit() != null) sensor.setUnit(dto.getUnit());
        if (dto.getStatus() != null) sensor.setStatus(dto.getStatus());
        if (dto.getWarningThreshold() != null) sensor.setWarningThreshold(dto.getWarningThreshold());
        if (dto.getAlarmThreshold() != null) sensor.setAlarmThreshold(dto.getAlarmThreshold());
        if (dto.getPowerOffThreshold() != null) sensor.setPowerOffThreshold(dto.getPowerOffThreshold());
        if (dto.getZoneCode() != null) sensor.setZoneCode(dto.getZoneCode());
        if (dto.getBatteryLevel() != null) sensor.setBatteryLevel(dto.getBatteryLevel());
        if (dto.getSignalStrength() != null) sensor.setSignalStrength(dto.getSignalStrength());
        if (dto.getDataUploadDelay() != null) sensor.setDataUploadDelay(dto.getDataUploadDelay());
        if (dto.getOfflineTimeoutMinutes() != null) sensor.setOfflineTimeoutMinutes(dto.getOfflineTimeoutMinutes());
        if (dto.getCalibrationCycleDays() != null) sensor.setCalibrationCycleDays(dto.getCalibrationCycleDays());

        sensor = sensorRepository.updateById(sensor);
        return convertToDTO(sensor);
    }

    /**
     * 删除传感器
     *
     * @param sensorId 传感器ID
     * @throws RuntimeException 传感器不存在时抛出
     */
    @Transactional
    public void deleteSensor(String sensorId) {
        Sensor sensor = sensorRepository.selectOne(
                new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, sensorId));
        if (sensor == null) {
            throw new RuntimeException("传感器不存在: " + sensorId);
        }
        sensorRepository.deleteById(sensor.getId());
    }

    /**
     * 获取传感器最新数据
     * 查询优先级：Redis缓存 → MySQL最新记录 → 内存缓存
     *
     * @param sensorId 传感器ID
     * @return 最新数据DTO（可能为null）
     */
    public SensorDataDTO getLatestSensorData(String sensorId) {
        String redisKey = "sensor:latest:" + sensorId;
        try {
            // 1. 优先从Redis缓存读取（最快）
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                return JSON.parseObject(cached, SensorDataDTO.class);
            }
        } catch (Exception e) {
            log.warn("读取Redis缓存失败: {}", e.getMessage());
        }

        // 2. 从MySQL查询最新一条记录
        SensorData data = sensorDataRepository.findLatestBySensorId(sensorId);
        if (data != null) {
            return convertToDataDTO(data);
        }

        // 3. 从内存缓存读取（兜底）
        return latestDataCache.get(sensorId);
    }

    /**
     * 查询传感器历史数据（原始数据）
     *
     * @param sensorId  传感器ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 历史数据列表
     */
    public List<SensorDataDTO> getSensorDataHistory(String sensorId, LocalDateTime startTime, LocalDateTime endTime) {
        return influxDBService.querySensorData(sensorId, startTime, endTime);
    }

    /**
     * 查询传感器历史数据（带聚合）
     *
     * @param sensorId    传感器ID
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @param aggregation 聚合函数（mean/max/min等）
     * @param window      时间窗口（1m/5m/1h等）
     * @return 聚合后的数据列表
     */
    public List<SensorDataDTO> getSensorDataAggregated(String sensorId, LocalDateTime startTime,
                                                         LocalDateTime endTime, String aggregation, String window) {
        return influxDBService.querySensorDataWithAggregation(sensorId, startTime, endTime, aggregation, window);
    }

    /**
     * 查询传感器统计数据（均值、最大值、最小值）
     *
     * @param sensorId  传感器ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计结果Map
     */
    public Map<String, Object> getSensorStatistics(String sensorId, LocalDateTime startTime, LocalDateTime endTime) {
        return influxDBService.queryStatistics(sensorId, startTime, endTime);
    }

    /**
     * 获取所有传感器的最新数据
     * 用于首页仪表盘展示，返回所有传感器的实时状态。
     *
     * @return 所有传感器的最新数据列表
     */
    public List<SensorDataDTO> getAllLatestData() {
        List<Sensor> sensors = sensorRepository.selectList(null);
        List<SensorDataDTO> result = new ArrayList<>();

        for (Sensor sensor : sensors) {
            try {
                SensorDataDTO latest = getLatestSensorData(sensor.getSensorId());
                if (latest != null) {
                    result.add(latest);
                } else {
                    // 无数据时返回基本信息
                    SensorDataDTO dto = new SensorDataDTO();
                    dto.setSensorId(sensor.getSensorId());
                    dto.setSensorType(sensor.getType());
                    dto.setLocation(sensor.getLocation());
                    dto.setUnit(sensor.getUnit());
                    result.add(dto);
                }
            } catch (Exception e) {
                log.warn("获取传感器最新数据失败: {}", sensor.getSensorId());
            }
        }

        return result;
    }

    /**
     * 更新内存缓存中的最新数据
     * 由MQTT/Kafka消费者在接收到新数据时调用。
     *
     * @param dto 最新传感器数据
     */
    public void updateLatestDataCache(SensorDataDTO dto) {
        latestDataCache.put(dto.getSensorId(), dto);
    }

    /**
     * 将Sensor实体转换为SensorDTO
     *
     * @param sensor 传感器实体
     * @return 传感器DTO
     */
    private SensorDTO convertToDTO(Sensor sensor) {
        SensorDTO dto = new SensorDTO();
        dto.setSensorId(sensor.getSensorId());
        dto.setName(sensor.getName());
        dto.setType(sensor.getType());
        dto.setProtocol(sensor.getProtocol());
        dto.setLocation(sensor.getLocation());
        dto.setCoordinatesX(sensor.getCoordinatesX());
        dto.setCoordinatesY(sensor.getCoordinatesY());
        dto.setCoordinatesZ(sensor.getCoordinatesZ());
        dto.setSamplingInterval(sensor.getSamplingInterval());
        dto.setMinValue(sensor.getMinValue());
        dto.setMaxValue(sensor.getMaxValue());
        dto.setUnit(sensor.getUnit());
        dto.setStatus(sensor.getStatus());
        dto.setWarningThreshold(sensor.getWarningThreshold());
        dto.setAlarmThreshold(sensor.getAlarmThreshold());
        dto.setPowerOffThreshold(sensor.getPowerOffThreshold());
        dto.setZoneCode(sensor.getZoneCode());
        dto.setLastOnlineTime(sensor.getLastOnlineTime() != null ?
                sensor.getLastOnlineTime().toString() : null);
        dto.setBatteryLevel(sensor.getBatteryLevel());
        dto.setSignalStrength(sensor.getSignalStrength());
        dto.setDataUploadDelay(sensor.getDataUploadDelay());
        dto.setOfflineTimeoutMinutes(sensor.getOfflineTimeoutMinutes());
        dto.setCalibrationCycleDays(sensor.getCalibrationCycleDays());
        dto.setLastCalibrationDate(sensor.getLastCalibrationDate() != null ?
                sensor.getLastCalibrationDate().toString() : null);
        dto.setNextCalibrationDate(sensor.getNextCalibrationDate() != null ?
                sensor.getNextCalibrationDate().toString() : null);
        return dto;
    }

    /**
     * 将SensorData实体转换为SensorDataDTO
     *
     * @param data 传感器数据实体
     * @return 传感器数据DTO
     */
    private SensorDataDTO convertToDataDTO(SensorData data) {
        SensorDataDTO dto = new SensorDataDTO();
        dto.setSensorId(data.getSensorId());
        dto.setValue(data.getValue());
        dto.setTimestamp(data.getTimestamp());
        dto.setLocation(data.getLocation());
        dto.setQuality(data.getQuality());
        return dto;
    }
}
