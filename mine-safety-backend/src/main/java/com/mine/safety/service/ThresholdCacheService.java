package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.domain.Sensor;
import com.mine.safety.dto.ThresholdDTO;
import com.mine.safety.repository.SensorRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThresholdCacheService {

    private final SensorRepository sensorRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.threshold.cache-expire-hours:24}")
    private long cacheExpireHours;

    private static final String THRESHOLD_CACHE_PREFIX = "threshold:sensor:";

    private final Map<String, ThresholdDTO> localCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("开始初始化阈值缓存...");
        try {
            for (Sensor sensor : sensorRepository.findAll()) {
                cacheThreshold(sensor);
            }
            log.info("阈值缓存初始化完成，共 {} 个传感器", localCache.size());
        } catch (Exception e) {
            log.warn("阈值缓存初始化失败: {}", e.getMessage());
        }
    }

    public ThresholdDTO getThreshold(String sensorId) {
        ThresholdDTO local = localCache.get(sensorId);
        if (local != null) {
            return local;
        }

        try {
            String cacheKey = THRESHOLD_CACHE_PREFIX + sensorId;
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                ThresholdDTO dto = JSON.parseObject(cached, ThresholdDTO.class);
                localCache.put(sensorId, dto);
                return dto;
            }
        } catch (Exception e) {
            log.warn("读取Redis阈值缓存失败: {}", e.getMessage());
        }

        Sensor sensor = sensorRepository.findBySensorId(sensorId).orElse(null);
        if (sensor != null) {
            ThresholdDTO dto = convertToDTO(sensor);
            cacheThreshold(sensor);
            return dto;
        }

        return null;
    }

    public BigDecimal getWarningThreshold(String sensorId) {
        ThresholdDTO dto = getThreshold(sensorId);
        return dto != null ? dto.getWarningThreshold() : null;
    }

    public BigDecimal getAlarmThreshold(String sensorId) {
        ThresholdDTO dto = getThreshold(sensorId);
        return dto != null ? dto.getAlarmThreshold() : null;
    }

    public BigDecimal getPowerOffThreshold(String sensorId) {
        ThresholdDTO dto = getThreshold(sensorId);
        return dto != null ? dto.getPowerOffThreshold() : null;
    }

    public void refreshThreshold(String sensorId) {
        localCache.remove(sensorId);
        String cacheKey = THRESHOLD_CACHE_PREFIX + sensorId;
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("删除Redis阈值缓存失败: {}", e.getMessage());
        }

        sensorRepository.findBySensorId(sensorId).ifPresent(this::cacheThreshold);
        log.info("阈值缓存已刷新 - 传感器: {}", sensorId);
    }

    public void refreshAllThresholds() {
        localCache.clear();
        try {
            redisTemplate.delete(redisTemplate.keys(THRESHOLD_CACHE_PREFIX + "*"));
        } catch (Exception e) {
            log.warn("批量删除Redis阈值缓存失败: {}", e.getMessage());
        }
        init();
        log.info("所有阈值缓存已刷新");
    }

    public void invalidateCache(String sensorId) {
        localCache.remove(sensorId);
        String cacheKey = THRESHOLD_CACHE_PREFIX + sensorId;
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("失效Redis阈值缓存失败: {}", e.getMessage());
        }
        log.debug("阈值缓存已失效 - 传感器: {}", sensorId);
    }

    public void cacheThreshold(Sensor sensor) {
        ThresholdDTO dto = convertToDTO(sensor);
        localCache.put(sensor.getSensorId(), dto);

        String cacheKey = THRESHOLD_CACHE_PREFIX + sensor.getSensorId();
        try {
            redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(dto),
                    cacheExpireHours, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("写入Redis阈值缓存失败: {}", e.getMessage());
        }
    }

    private ThresholdDTO convertToDTO(Sensor sensor) {
        ThresholdDTO dto = new ThresholdDTO();
        dto.setSensorId(sensor.getSensorId());
        dto.setSensorName(sensor.getName());
        dto.setSensorType(sensor.getType());
        dto.setWarningThreshold(sensor.getWarningThreshold());
        dto.setAlarmThreshold(sensor.getAlarmThreshold());
        dto.setPowerOffThreshold(sensor.getPowerOffThreshold());
        dto.setUpdatedAt(sensor.getUpdatedAt());
        return dto;
    }
}
