package com.mine.safety.service;

import com.mine.safety.domain.Sensor;
import com.mine.safety.dto.RealtimeMonitorDTO;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
public class RealtimeMonitorService {

    private final SensorRepository sensorRepository;
    private final SensorService sensorService;
    private final WebSocketPushService webSocketPushService;

    private final Map<String, SensorDataDTO> latestSensorData = new ConcurrentHashMap<>();

    public void updateSensorData(SensorDataDTO data) {
        latestSensorData.put(data.getSensorId(), data);
    }

    public RealtimeMonitorDTO getChannelMonitor(String sensorId) {
        Sensor sensor = sensorRepository.findBySensorId(sensorId)
                .orElseThrow(() -> new RuntimeException("传感器不存在: " + sensorId));

        SensorDataDTO latestData = latestSensorData.get(sensorId);
        if (latestData == null) {
            latestData = sensorService.getLatestSensorData(sensorId);
        }

        return buildRealtimeMonitorDTO(sensor, latestData);
    }

    public RealtimeMonitorDTO.ZoneMonitorDTO getZoneMonitor(String zoneCode) {
        List<Sensor> sensors = sensorRepository.findByZoneCode(zoneCode);

        List<RealtimeMonitorDTO> sensorMonitors = new ArrayList<>();
        int alertCount = 0, warningCount = 0, emergencyCount = 0;

        for (Sensor sensor : sensors) {
            SensorDataDTO latestData = latestSensorData.get(sensor.getSensorId());
            if (latestData == null) {
                latestData = sensorService.getLatestSensorData(sensor.getSensorId());
            }
            RealtimeMonitorDTO monitor = buildRealtimeMonitorDTO(sensor, latestData);
            sensorMonitors.add(monitor);

            if ("EMERGENCY".equals(monitor.getAlertLevel())) emergencyCount++;
            else if ("ALERT".equals(monitor.getAlertLevel())) alertCount++;
            else if ("WARNING".equals(monitor.getAlertLevel())) warningCount++;
        }

        RealtimeMonitorDTO.ZoneMonitorDTO zoneMonitor = new RealtimeMonitorDTO.ZoneMonitorDTO();
        zoneMonitor.setZoneCode(zoneCode);
        zoneMonitor.setZoneName(getZoneName(zoneCode));
        zoneMonitor.setZoneType(getZoneType(zoneCode));
        zoneMonitor.setSensors(sensorMonitors);
        zoneMonitor.setAlertCount(alertCount);
        zoneMonitor.setWarningCount(warningCount);
        zoneMonitor.setEmergencyCount(emergencyCount);

        return zoneMonitor;
    }

    public RealtimeMonitorDTO.MineMonitorDTO getMineMonitor() {
        List<Sensor> allSensors = sensorRepository.findAll();
        Map<String, List<Sensor>> zoneSensorMap = allSensors.stream()
                .filter(s -> s.getZoneCode() != null)
                .collect(Collectors.groupingBy(Sensor::getZoneCode));

        List<RealtimeMonitorDTO.ZoneMonitorDTO> zones = new ArrayList<>();
        int totalAlert = 0, totalWarning = 0, totalEmergency = 0;
        int onlineCount = 0;

        for (Map.Entry<String, List<Sensor>> entry : zoneSensorMap.entrySet()) {
            RealtimeMonitorDTO.ZoneMonitorDTO zoneMonitor = getZoneMonitor(entry.getKey());
            zones.add(zoneMonitor);
            totalAlert += zoneMonitor.getAlertCount();
            totalWarning += zoneMonitor.getWarningCount();
            totalEmergency += zoneMonitor.getEmergencyCount();
        }

        for (Sensor sensor : allSensors) {
            if (sensor.getStatus() != null && sensor.getStatus() == 1) {
                onlineCount++;
            }
        }

        RealtimeMonitorDTO.MineMonitorDTO mineMonitor = new RealtimeMonitorDTO.MineMonitorDTO();
        mineMonitor.setMineCode("MINE-001");
        mineMonitor.setMineName("一号矿井");
        mineMonitor.setZones(zones);
        mineMonitor.setTotalSensors(allSensors.size());
        mineMonitor.setOnlineSensors(onlineCount);
        mineMonitor.setAlertCount(totalAlert);
        mineMonitor.setWarningCount(totalWarning);
        mineMonitor.setEmergencyCount(totalEmergency);
        mineMonitor.setUpdateTime(LocalDateTime.now());

        return mineMonitor;
    }

    @Scheduled(fixedRate = 5000)
    public void pushRealtimeMonitorData() {
        try {
            RealtimeMonitorDTO.MineMonitorDTO monitorData = getMineMonitor();
            webSocketPushService.pushRealtimeMonitor(monitorData);

            Map<String, List<Sensor>> zoneSensorMap = sensorRepository.findAll().stream()
                    .filter(s -> s.getZoneCode() != null)
                    .collect(Collectors.groupingBy(Sensor::getZoneCode));

            for (String zoneCode : zoneSensorMap.keySet()) {
                RealtimeMonitorDTO.ZoneMonitorDTO zoneMonitor = getZoneMonitor(zoneCode);
                webSocketPushService.pushZoneMonitor(zoneMonitor);
            }

            log.debug("实时监测数据已推送");
        } catch (Exception e) {
            log.warn("推送实时监测数据失败: {}", e.getMessage());
        }
    }

    private RealtimeMonitorDTO buildRealtimeMonitorDTO(Sensor sensor, SensorDataDTO latestData) {
        RealtimeMonitorDTO dto = new RealtimeMonitorDTO();
        dto.setSensorId(sensor.getSensorId());
        dto.setSensorName(sensor.getName());
        dto.setSensorType(sensor.getType());
        dto.setLocation(sensor.getLocation());
        dto.setWarningThreshold(sensor.getWarningThreshold());
        dto.setAlarmThreshold(sensor.getAlarmThreshold());
        dto.setPowerOffThreshold(sensor.getPowerOffThreshold());
        dto.setUnit(sensor.getUnit());

        if (latestData != null) {
            dto.setCurrentValue(latestData.getValue());
            dto.setTimestamp(latestData.getTimestamp());
            dto.setAlertLevel(determineAlertLevel(latestData.getValue(), sensor));
        } else {
            dto.setAlertLevel("NORMAL");
        }

        return dto;
    }

    private String determineAlertLevel(BigDecimal value, Sensor sensor) {
        if (value == null) return "NORMAL";

        if (sensor.getPowerOffThreshold() != null && value.compareTo(sensor.getPowerOffThreshold()) >= 0) {
            return "EMERGENCY";
        }
        if (sensor.getAlarmThreshold() != null && value.compareTo(sensor.getAlarmThreshold()) >= 0) {
            return "ALERT";
        }
        if (sensor.getWarningThreshold() != null && value.compareTo(sensor.getWarningThreshold()) >= 0) {
            return "WARNING";
        }
        return "NORMAL";
    }

    private String getZoneName(String zoneCode) {
        return switch (zoneCode) {
            case "ZONE-001" -> "一号矿井";
            case "ZONE-002" -> "综采工作面";
            case "ZONE-003" -> "掘进工作面";
            case "ZONE-004" -> "回风巷";
            case "ZONE-005" -> "主运输大巷";
            case "ZONE-006" -> "机电硐室";
            default -> "未知区域";
        };
    }

    private String getZoneType(String zoneCode) {
        return switch (zoneCode) {
            case "ZONE-001" -> "MINE";
            case "ZONE-002" -> "采煤工作面";
            case "ZONE-003" -> "掘进工作面";
            case "ZONE-004" -> "回风巷";
            case "ZONE-005" -> "运输巷";
            case "ZONE-006" -> "机电硐室";
            default -> "UNKNOWN";
        };
    }
}
