package com.mine.safety.flink.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SensorData {
    private String sensorId;
    private String sensorType;
    private BigDecimal value;
    private LocalDateTime timestamp;
    private String location;
    private Integer quality;
}
