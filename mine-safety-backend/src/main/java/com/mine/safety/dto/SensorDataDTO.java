package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SensorDataDTO {

    private String sensorId;
    private BigDecimal value;
    private LocalDateTime timestamp;
    private String location;
    private Double coordinatesX;
    private Double coordinatesY;
    private Double coordinatesZ;
    private String unit;
    private String sensorType;
    private Integer quality = 1;
    private String protocol;
    private String gatewayId;
    private Long edgeNodeId;
}
