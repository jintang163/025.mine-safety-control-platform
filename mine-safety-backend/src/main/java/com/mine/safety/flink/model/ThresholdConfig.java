package com.mine.safety.flink.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ThresholdConfig {
    private String sensorId;
    private String sensorName;
    private String sensorType;
    private BigDecimal warningThreshold;
    private BigDecimal alarmThreshold;
    private BigDecimal powerOffThreshold;
}
