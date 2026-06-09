package com.mine.safety.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mine.safety.domain.Sensor;
import com.mine.safety.domain.SensorData;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.repository.SensorDataRepository;
import com.mine.safety.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 传感器数据处理服务
 * 核心数据处理组件，负责对MQTT/Kafka接收的原始传感器数据进行预处理、
 * 异常检测、去噪、补全，并持久化到MySQL、InfluxDB和Redis缓存。
 *
 * 数据处理流程：
 *   1. 传感器配置验证（从MySQL读取传感器元数据）
 *   2. 异常检测（Z-Score算法，3σ原则）
 *   3. 指数平滑去噪（α可配置）
 *   4. 元数据补全（位置、单位、类型、坐标）
 *   5. 传感器状态更新（标记为在线）
 *   6. 数据持久化（MySQL/InfluxDB双写）
 *   7. Redis缓存更新（供实时查询使用）
 *
 * @author mine-safety
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataService {

    /** 传感器配置Repository，用于读取传感器元数据 */
    private final SensorRepository sensorRepository;

    /** 传感器数据Repository，用于存储历史数据到MySQL */
    private final SensorDataRepository sensorDataRepository;

    /** InfluxDB服务，用于时序数据存储 */
    private final InfluxDBService influxDBService;

    /** Redis模板，用于实时数据缓存 */
    private final StringRedisTemplate redisTemplate;

    /**
     * 指数平滑去噪系数α（默认0.3）
     * 取值范围0~1，值越大越重视新数据，平滑效果越弱
     */
    @Value("${app.data-processing.denoise-alpha:0.3}")
    private double denoiseAlpha;

    /** 是否启用异常检测（默认启用） */
    @Value("${app.data-processing.anomaly-detection.enabled:true}")
    private boolean anomalyDetectionEnabled;

    /**
     * Z-Score异常检测阈值（默认3.0，即3σ原则）
     * 数据点偏离均值超过该倍数标准差则判定为异常
     */
    @Value("${app.data-processing.anomaly-detection.z-score-threshold:3.0}")
    private double zScoreThreshold;

    /**
     * 每个传感器的统计信息缓存（滑动窗口100个样本）
     * key: sensorId，value: 统计对象（包含均值、标准差等）
     */
    private final ConcurrentHashMap<String, DescriptiveStatistics> statsCache = new ConcurrentHashMap<>();

    /**
     * 每个传感器的上一个值缓存（用于指数平滑去噪）
     * key: sensorId，value: 上一个去噪后的值
     */
    private final ConcurrentHashMap<String, Double> lastValueCache = new ConcurrentHashMap<>();

    /**
     * 处理传感器数据（核心方法）
     * 对原始传感器数据执行完整的处理流程，
     * 包括异常检测、去噪、补全、状态更新和持久化。
     *
     * @param dto 原始传感器数据DTO
     * @return 处理后的传感器数据DTO（包含去噪后的值、质量标记、补全的元数据）
     */
    @Transactional
    public SensorDataDTO processSensorData(SensorDataDTO dto) {
        String sensorId = dto.getSensorId();
        BigDecimal value = dto.getValue();

        // 1. 验证传感器配置
        Sensor sensor = sensorRepository.selectOne(
                new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, sensorId));
        if (sensor == null) {
            log.warn("未找到传感器配置: {}", sensorId);
            dto.setQuality(0);
            saveToInfluxDB(dto);
            return dto;
        }

        // 2. 异常检测（Z-Score算法）
        if (anomalyDetectionEnabled) {
            boolean isNormal = checkAnomaly(sensorId, value);
            dto.setQuality(isNormal ? 1 : 0);
        }

        // 3. 指数平滑去噪
        BigDecimal denoisedValue = applyDenoise(sensorId, value);
        dto.setValue(denoisedValue);

        // 4. 元数据补全（使用数据库中的传感器配置）
        if (dto.getLocation() == null && sensor.getLocation() != null) {
            dto.setLocation(sensor.getLocation());
        }
        if (dto.getUnit() == null && sensor.getUnit() != null) {
            dto.setUnit(sensor.getUnit());
        }
        if (dto.getSensorType() == null && sensor.getType() != null) {
            dto.setSensorType(sensor.getType());
        }
        if (dto.getCoordinatesX() == null && sensor.getCoordinatesX() != null) {
            dto.setCoordinatesX(sensor.getCoordinatesX().doubleValue());
            dto.setCoordinatesY(sensor.getCoordinatesY().doubleValue());
            dto.setCoordinatesZ(sensor.getCoordinatesZ().doubleValue());
        }

        // 5. 更新传感器在线状态
        updateSensorStatus(sensorId, dto.getTimestamp());

        // 6. 数据持久化（MySQL + InfluxDB）
        saveToMySQL(dto);
        saveToInfluxDB(dto);

        // 7. 更新Redis缓存（5分钟过期）
        updateRedisCache(sensorId, dto);

        return dto;
    }

    /**
     * Z-Score异常检测
     * 基于统计学的3σ原则，判断数据点是否偏离历史数据分布。
     *
     * 算法原理：
     *   1. 维护每个传感器最近100个样本的滑动窗口
     *   2. 计算窗口内的均值(μ)和标准差(σ)
     *   3. 计算当前值的Z-Score = |(x - μ) / σ|
     *   4. Z-Score > 阈值（默认3）则判定为异常
     *
     * @param sensorId 传感器ID
     * @param value    当前测量值
     * @return true-正常，false-异常
     */
    private boolean checkAnomaly(String sensorId, BigDecimal value) {
        // 获取或创建传感器的统计窗口（最多保存100个样本）
        DescriptiveStatistics stats = statsCache.computeIfAbsent(sensorId, k -> new DescriptiveStatistics(100));
        stats.addValue(value.doubleValue());

        // 样本数不足时不进行检测，避免误判
        if (stats.getN() < 10) {
            return true;
        }

        // 计算均值和标准差
        double mean = stats.getMean();
        double std = stats.getStandardDeviation();
        if (std == 0) std = 1.0;  // 防止除零

        // 计算Z-Score并判断
        double zScore = Math.abs((value.doubleValue() - mean) / std);
        boolean isNormal = zScore <= zScoreThreshold;

        if (!isNormal) {
            log.warn("检测到异常数据 - 传感器: {}, 值: {}, Z-Score: {}", sensorId, value, zScore);
        }

        return isNormal;
    }

    /**
     * 指数平滑去噪
     * 使用一阶指数平滑算法对传感器数据进行去噪处理，
     * 保留数据趋势的同时平滑掉高频噪声。
     *
     * 公式：smoothed = last + α × (current - last)
     *   - last: 上一次平滑后的值
     *   - current: 当前测量值
     *   - α: 平滑系数（0~1），值越大越敏感，平滑效果越弱
     *
     * @param sensorId 传感器ID
     * @param newValue 当前测量值
     * @return 去噪后的值
     */
    private BigDecimal applyDenoise(String sensorId, BigDecimal newValue) {
        Double lastValue = lastValueCache.get(sensorId);
        // 首次接收数据时直接返回原值
        if (lastValue == null) {
            lastValueCache.put(sensorId, newValue.doubleValue());
            return newValue;
        }

        // 指数平滑计算
        double smoothed = lastValue + denoiseAlpha * (newValue.doubleValue() - lastValue);
        lastValueCache.put(sensorId, smoothed);
        return BigDecimal.valueOf(smoothed);
    }

    /**
     * 保存数据到MySQL（历史数据表）
     * 用于结构化存储，支持复杂查询和数据备份。
     *
     * @param dto 处理后的传感器数据
     */
    private void saveToMySQL(SensorDataDTO dto) {
        try {
            SensorData sensorData = new SensorData();
            sensorData.setSensorId(dto.getSensorId());
            sensorData.setValue(dto.getValue());
            sensorData.setTimestamp(dto.getTimestamp());
            sensorData.setLocation(dto.getLocation());
            sensorData.setQuality(dto.getQuality());
            sensorDataRepository.insert(sensorData);
        } catch (Exception e) {
            log.error("保存传感器数据到MySQL失败: {}", e.getMessage());
        }
    }

    /**
     * 保存数据到InfluxDB（时序数据库）
     * 用于高性能时序数据存储，支持大规模写入和复杂时序查询。
     *
     * @param dto 处理后的传感器数据
     */
    private void saveToInfluxDB(SensorDataDTO dto) {
        try {
            influxDBService.writeSensorData(dto);
        } catch (Exception e) {
            log.error("保存传感器数据到InfluxDB失败: {}", e.getMessage());
        }
    }

    /**
     * 更新Redis缓存（实时数据）
     * 保存每个传感器的最新数据，供前端实时查询使用。
     * 缓存过期时间5分钟，确保数据新鲜度。
     *
     * @param sensorId 传感器ID
     * @param dto      处理后的传感器数据
     */
    private void updateRedisCache(String sensorId, SensorDataDTO dto) {
        try {
            String key = "sensor:latest:" + sensorId;
            redisTemplate.opsForValue().set(key, com.alibaba.fastjson2.JSON.toJSONString(dto), 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("更新Redis缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 更新传感器在线状态
     * 每次收到数据时更新传感器的lastOnlineTime和状态为在线。
     *
     * @param sensorId  传感器ID
     * @param timestamp 数据时间戳
     */
    @Transactional
    public void updateSensorStatus(String sensorId, LocalDateTime timestamp) {
        try {
            sensorRepository.updateSensorStatus(sensorId, Sensor.Status.ONLINE.getValue(), timestamp);
        } catch (Exception e) {
            log.error("更新传感器状态失败: {}", e.getMessage());
        }
    }
}
