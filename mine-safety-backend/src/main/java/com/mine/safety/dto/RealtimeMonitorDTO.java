package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RealtimeMonitorDTO {
    private String sensorId;
    private String sensorName;
    private String sensorType;
    private String location;
    private BigDecimal currentValue;
    private BigDecimal warningThreshold;
    private BigDecimal alarmThreshold;
    private BigDecimal powerOffThreshold;
    private String alertLevel;
    private LocalDateTime timestamp;
    private String unit;

    @Data
    public static class ZoneMonitorDTO {
        private String zoneCode;
        private String zoneName;
        private String zoneType;
        private List<RealtimeMonitorDTO> sensors;
        private int alertCount;
        private int warningCount;
        private int emergencyCount;
    }

    @Data
    public static class MineMonitorDTO {
        private String mineCode;
        private String mineName;
        private List<ZoneMonitorDTO> zones;
        private int totalSensors;
        private int onlineSensors;
        private int alertCount;
        private int warningCount;
        private int emergencyCount;
        private LocalDateTime updateTime;
    }
}
