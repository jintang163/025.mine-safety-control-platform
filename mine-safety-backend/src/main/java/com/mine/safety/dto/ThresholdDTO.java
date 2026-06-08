package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ThresholdDTO {
    private String sensorId;
    private String sensorName;
    private String sensorType;
    private BigDecimal warningThreshold;
    private BigDecimal alarmThreshold;
    private BigDecimal powerOffThreshold;
    private LocalDateTime updatedAt;
}
