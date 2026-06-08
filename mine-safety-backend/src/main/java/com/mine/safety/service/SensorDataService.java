package com.mine.safety.service;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataService {

    private final SensorRepository sensorRepository;
    private final SensorDataRepository sensorDataRepository;
    private final InfluxDBService influxDBService;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.data-processing.denoise-alpha:0.3}")
    private double denoiseAlpha;

    @Value("${app.data-processing.anomaly-detection.enabled:true}")
    private boolean anomalyDetectionEnabled;

    @Value("${app.data-processing.anomaly-detection.z-score-threshold:3.0}")
    private double zScoreThreshold;

    private final ConcurrentHashMap<String, DescriptiveStatistics> statsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> lastValueCache = new ConcurrentHashMap<>();

    @Transactional
    public SensorDataDTO processSensorData(SensorDataDTO dto) {
        String sensorId = dto.getSensorId();
        BigDecimal value = dto.getValue();

        Sensor sensor = sensorRepository.findBySensorId(sensorId).orElse(null);

        if (sensor == null) {
            log.warn("未找到传感器配置: {}", sensorId);
            dto.setQuality(0);
            saveToInfluxDB(dto);
            return dto;
        }

        if (anomalyDetectionEnabled) {
            boolean isNormal = checkAnomaly(sensorId, value);
            dto.setQuality(isNormal ? 1 : 0);
        }

        BigDecimal denoisedValue = applyDenoise(sensorId, value);
        dto.setValue(denoisedValue);

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

        updateSensorStatus(sensorId, dto.getTimestamp());

        saveToMySQL(dto);
        saveToInfluxDB(dto);
        updateRedisCache(sensorId, dto);

        return dto;
    }

    private boolean checkAnomaly(String sensorId, BigDecimal value) {
        DescriptiveStatistics stats = statsCache.computeIfAbsent(sensorId, k -> new DescriptiveStatistics(100));
        stats.addValue(value.doubleValue());

        if (stats.getN() < 10) {
            return true;
        }

        double mean = stats.getMean();
        double std = stats.getStandardDeviation();
        if (std == 0) std = 1.0;

        double zScore = Math.abs((value.doubleValue() - mean) / std);
        boolean isNormal = zScore <= zScoreThreshold;

        if (!isNormal) {
            log.warn("检测到异常数据 - 传感器: {}, 值: {}, Z-Score: {}", sensorId, value, zScore);
        }

        return isNormal;
    }

    private BigDecimal applyDenoise(String sensorId, BigDecimal newValue) {
        Double lastValue = lastValueCache.get(sensorId);
        if (lastValue == null) {
            lastValueCache.put(sensorId, newValue.doubleValue());
            return newValue;
        }

        double smoothed = lastValue + denoiseAlpha * (newValue.doubleValue() - lastValue);
        lastValueCache.put(sensorId, smoothed);
        return BigDecimal.valueOf(smoothed);
    }

    private void saveToMySQL(SensorDataDTO dto) {
        try {
            SensorData sensorData = new SensorData();
            sensorData.setSensorId(dto.getSensorId());
            sensorData.setValue(dto.getValue());
            sensorData.setTimestamp(dto.getTimestamp());
            sensorData.setLocation(dto.getLocation());
            sensorData.setQuality(dto.getQuality());
            sensorDataRepository.save(sensorData);
        } catch (Exception e) {
            log.error("保存传感器数据到MySQL失败: {}", e.getMessage());
        }
    }

    private void saveToInfluxDB(SensorDataDTO dto) {
        try {
            influxDBService.writeSensorData(dto);
        } catch (Exception e) {
            log.error("保存传感器数据到InfluxDB失败: {}", e.getMessage());
        }
    }

    private void updateRedisCache(String sensorId, SensorDataDTO dto) {
        try {
            String key = "sensor:latest:" + sensorId;
            redisTemplate.opsForValue().set(key, com.alibaba.fastjson2.JSON.toJSONString(dto), 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("更新Redis缓存失败: {}", e.getMessage());
        }
    }

    @Transactional
    public void updateSensorStatus(String sensorId, LocalDateTime timestamp) {
        try {
            sensorRepository.updateSensorStatus(sensorId, Sensor.Status.ONLINE.getValue(), timestamp);
        } catch (Exception e) {
            log.error("更新传感器状态失败: {}", e.getMessage());
        }
    }
}
