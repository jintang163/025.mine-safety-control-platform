package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.domain.Sensor;
import com.mine.safety.domain.SensorData;
import com.mine.safety.dto.SensorDTO;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.repository.SensorDataRepository;
import com.mine.safety.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorService {

    private final SensorRepository sensorRepository;
    private final SensorDataRepository sensorDataRepository;
    private final StringRedisTemplate redisTemplate;
    private final InfluxDBService influxDBService;

    private final Map<String, SensorDataDTO> latestDataCache = new ConcurrentHashMap<>();

    public List<SensorDTO> getAllSensors() {
        return sensorRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<SensorDTO> getSensorsByType(String type) {
        return sensorRepository.findByType(type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<SensorDTO> getSensorsByStatus(Integer status) {
        return sensorRepository.findByStatus(status).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public SensorDTO getSensorById(String sensorId) {
        return sensorRepository.findBySensorId(sensorId)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("传感器不存在: " + sensorId));
    }

    @Transactional
    public SensorDTO createSensor(SensorDTO dto) {
        if (sensorRepository.existsBySensorId(dto.getSensorId())) {
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

        sensor = sensorRepository.save(sensor);
        return convertToDTO(sensor);
    }

    @Transactional
    public SensorDTO updateSensor(String sensorId, SensorDTO dto) {
        Sensor sensor = sensorRepository.findBySensorId(sensorId)
                .orElseThrow(() -> new RuntimeException("传感器不存在: " + sensorId));

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

        sensor = sensorRepository.save(sensor);
        return convertToDTO(sensor);
    }

    @Transactional
    public void deleteSensor(String sensorId) {
        Sensor sensor = sensorRepository.findBySensorId(sensorId)
                .orElseThrow(() -> new RuntimeException("传感器不存在: " + sensorId));
        sensorRepository.delete(sensor);
    }

    public SensorDataDTO getLatestSensorData(String sensorId) {
        String redisKey = "sensor:latest:" + sensorId;
        try {
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                return JSON.parseObject(cached, SensorDataDTO.class);
            }
        } catch (Exception e) {
            log.warn("读取Redis缓存失败: {}", e.getMessage());
        }

        SensorData data = sensorDataRepository.findLatestBySensorId(sensorId);
        if (data != null) {
            return convertToDataDTO(data);
        }

        return latestDataCache.get(sensorId);
    }

    public List<SensorDataDTO> getSensorDataHistory(String sensorId, LocalDateTime startTime, LocalDateTime endTime) {
        return influxDBService.querySensorData(sensorId, startTime, endTime);
    }

    public List<SensorDataDTO> getSensorDataAggregated(String sensorId, LocalDateTime startTime,
                                                         LocalDateTime endTime, String aggregation, String window) {
        return influxDBService.querySensorDataWithAggregation(sensorId, startTime, endTime, aggregation, window);
    }

    public Map<String, Object> getSensorStatistics(String sensorId, LocalDateTime startTime, LocalDateTime endTime) {
        return influxDBService.queryStatistics(sensorId, startTime, endTime);
    }

    public List<SensorDataDTO> getAllLatestData() {
        List<Sensor> sensors = sensorRepository.findAll();
        List<SensorDataDTO> result = new ArrayList<>();

        for (Sensor sensor : sensors) {
            try {
                SensorDataDTO latest = getLatestSensorData(sensor.getSensorId());
                if (latest != null) {
                    result.add(latest);
                } else {
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

    @Scheduled(fixedRate = 30000)
    public void checkSensorOffline() {
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(5);
        List<Sensor> sensors = sensorRepository.findSensorsToCheckOffline(Sensor.Status.ONLINE.getValue(), timeout);

        for (Sensor sensor : sensors) {
            sensorRepository.updateSensorStatus(sensor.getSensorId(),
                    Sensor.Status.OFFLINE.getValue(), sensor.getLastOnlineTime());
            log.warn("传感器已离线: {} - {}", sensor.getSensorId(), sensor.getName());
        }
    }

    public void updateLatestDataCache(SensorDataDTO dto) {
        latestDataCache.put(dto.getSensorId(), dto);
    }

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
        dto.setLastOnlineTime(sensor.getLastOnlineTime() != null ?
                sensor.getLastOnlineTime().toString() : null);
        return dto;
    }

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
