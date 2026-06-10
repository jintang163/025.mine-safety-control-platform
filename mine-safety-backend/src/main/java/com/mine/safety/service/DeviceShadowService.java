package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mine.safety.config.MqttConfig;
import com.mine.safety.domain.Sensor;
import com.mine.safety.domain.SensorDeviceShadow;
import com.mine.safety.dto.DeviceShadowDTO;
import com.mine.safety.repository.SensorDeviceShadowRepository;
import com.mine.safety.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceShadowService {

    private final SensorDeviceShadowRepository shadowRepository;
    private final SensorRepository sensorRepository;
    private final MqttConfig mqttConfig;

    public DeviceShadowDTO getShadow(String sensorId) {
        SensorDeviceShadow shadow = shadowRepository.selectOne(
                new LambdaQueryWrapper<SensorDeviceShadow>().eq(SensorDeviceShadow::getSensorId, sensorId));
        if (shadow == null) {
            return null;
        }
        return convertToDTO(shadow);
    }

    @Transactional
    public DeviceShadowDTO updateDesiredState(String sensorId, Map<String, Object> desiredConfig) {
        SensorDeviceShadow shadow = shadowRepository.selectOne(
                new LambdaQueryWrapper<SensorDeviceShadow>().eq(SensorDeviceShadow::getSensorId, sensorId));

        if (shadow == null) {
            shadow = new SensorDeviceShadow();
            shadow.setSensorId(sensorId);
            shadow.setReportedState("{}");
            shadow.setReportedVersion(0);
        }

        shadow.setDesiredState(JSON.toJSONString(desiredConfig));
        shadow.setDesiredVersion(shadow.getDesiredVersion() + 1);
        shadow.setLastDesiredTime(LocalDateTime.now());
        shadow.setSyncStatus(SensorDeviceShadow.SyncStatus.PENDING.name());

        Map<String, Object> delta = computeDelta(shadow.getReportedState(), shadow.getDesiredState());
        shadow.setDelta(JSON.toJSONString(delta));

        if (shadow.getId() == null) {
            shadowRepository.insert(shadow);
        } else {
            shadowRepository.updateById(shadow);
        }

        pushDesiredConfigViaMqtt(sensorId, desiredConfig, shadow.getDesiredVersion());

        log.info("设备影子期望状态已更新 - 传感器: {}, 版本: {}", sensorId, shadow.getDesiredVersion());
        return convertToDTO(shadow);
    }

    @Transactional
    public void handleReportedState(String sensorId, Map<String, Object> reportedState) {
        SensorDeviceShadow shadow = shadowRepository.selectOne(
                new LambdaQueryWrapper<SensorDeviceShadow>().eq(SensorDeviceShadow::getSensorId, sensorId));

        if (shadow == null) {
            shadow = new SensorDeviceShadow();
            shadow.setSensorId(sensorId);
            shadow.setDesiredState("{}");
            shadow.setDesiredVersion(0);
        }

        shadow.setReportedState(JSON.toJSONString(reportedState));
        shadow.setReportedVersion(shadow.getReportedVersion() + 1);
        shadow.setLastReportedTime(LocalDateTime.now());

        Map<String, Object> delta = computeDelta(shadow.getReportedState(), shadow.getDesiredState());
        shadow.setDelta(JSON.toJSONString(delta));

        if (delta.isEmpty()) {
            shadow.setSyncStatus(SensorDeviceShadow.SyncStatus.SYNCED.name());
            log.info("设备影子已同步 - 传感器: {}", sensorId);
        } else {
            shadow.setSyncStatus(SensorDeviceShadow.SyncStatus.PENDING.name());
            log.info("设备影子存在差异 - 传感器: {}, 差异: {}", sensorId, delta);
        }

        if (shadow.getId() == null) {
            shadowRepository.insert(shadow);
        } else {
            shadowRepository.updateById(shadow);
        }

        updateSensorRealtimeStatus(sensorId, reportedState);
    }

    @Transactional
    public DeviceShadowDTO otaUpdateSamplingInterval(String sensorId, Integer samplingInterval) {
        Sensor sensor = sensorRepository.selectOne(
                new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, sensorId));
        if (sensor == null) {
            throw new RuntimeException("传感器不存在: " + sensorId);
        }

        Map<String, Object> desiredConfig = new HashMap<>();
        if (sensor.getWarningThreshold() != null) {
            desiredConfig.put("warningThreshold", sensor.getWarningThreshold());
        }
        if (sensor.getAlarmThreshold() != null) {
            desiredConfig.put("alarmThreshold", sensor.getAlarmThreshold());
        }
        if (sensor.getPowerOffThreshold() != null) {
            desiredConfig.put("powerOffThreshold", sensor.getPowerOffThreshold());
        }
        desiredConfig.put("samplingInterval", samplingInterval);

        return updateDesiredState(sensorId, desiredConfig);
    }

    @Transactional
    public DeviceShadowDTO otaUpdateThresholds(String sensorId, Map<String, Object> thresholds) {
        return updateDesiredState(sensorId, thresholds);
    }

    private Map<String, Object> computeDelta(String reportedJson, String desiredJson) {
        Map<String, Object> delta = new HashMap<>();
        try {
            JSONObject reported = JSON.parseObject(reportedJson);
            JSONObject desired = JSON.parseObject(desiredJson);

            if (desired == null || desired.isEmpty()) {
                return delta;
            }

            for (Map.Entry<String, Object> entry : desired.entrySet()) {
                Object desiredValue = entry.getValue();
                Object reportedValue = reported != null ? reported.get(entry.getKey()) : null;

                if (reportedValue == null || !reportedValue.equals(desiredValue)) {
                    delta.put(entry.getKey(), desiredValue);
                }
            }
        } catch (Exception e) {
            log.warn("计算设备影子差异失败: {}", e.getMessage());
        }
        return delta;
    }

    private void pushDesiredConfigViaMqtt(String sensorId, Map<String, Object> desiredConfig, int version) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sensorId", sensorId);
            payload.put("version", version);
            payload.put("config", desiredConfig);
            payload.put("timestamp", System.currentTimeMillis());

            String topic = "mine/shadow/desired/" + sensorId;
            mqttConfig.publish(topic, JSON.toJSONString(payload), 1, true);
            log.info("OTA配置已通过MQTT下发 - 传感器: {}, 版本: {}", sensorId, version);
        } catch (Exception e) {
            log.error("OTA配置下发失败 - 传感器: {}, 错误: {}", sensorId, e.getMessage(), e);
        }
    }

    private void updateSensorRealtimeStatus(String sensorId, Map<String, Object> reportedState) {
        try {
            Sensor sensor = sensorRepository.selectOne(
                    new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, sensorId));
            if (sensor == null) {
                return;
            }

            if (reportedState.containsKey("batteryLevel")) {
                sensor.setBatteryLevel(((Number) reportedState.get("batteryLevel")).intValue());
            }
            if (reportedState.containsKey("signalStrength")) {
                sensor.setSignalStrength(((Number) reportedState.get("signalStrength")).intValue());
            }
            if (reportedState.containsKey("dataUploadDelay")) {
                sensor.setDataUploadDelay(((Number) reportedState.get("dataUploadDelay")).intValue());
            }

            sensor.setLastOnlineTime(LocalDateTime.now());
            if (sensor.getStatus() != null && sensor.getStatus() == Sensor.Status.OFFLINE.getValue()) {
                sensor.setStatus(Sensor.Status.ONLINE.getValue());
            }
            sensorRepository.updateById(sensor);
        } catch (Exception e) {
            log.warn("更新传感器实时状态失败 - 传感器: {}, 错误: {}", sensorId, e.getMessage());
        }
    }

    private DeviceShadowDTO convertToDTO(SensorDeviceShadow shadow) {
        DeviceShadowDTO dto = new DeviceShadowDTO();
        dto.setId(shadow.getId());
        dto.setSensorId(shadow.getSensorId());
        dto.setReportedState(shadow.getReportedState() != null ? JSON.parseObject(shadow.getReportedState()) : null);
        dto.setDesiredState(shadow.getDesiredState() != null ? JSON.parseObject(shadow.getDesiredState()) : null);
        dto.setReportedVersion(shadow.getReportedVersion());
        dto.setDesiredVersion(shadow.getDesiredVersion());
        dto.setLastReportedTime(shadow.getLastReportedTime() != null ? shadow.getLastReportedTime().toString() : null);
        dto.setLastDesiredTime(shadow.getLastDesiredTime() != null ? shadow.getLastDesiredTime().toString() : null);
        dto.setSyncStatus(shadow.getSyncStatus());
        dto.setDelta(shadow.getDelta() != null ? JSON.parseObject(shadow.getDelta()) : null);
        return dto;
    }
}
