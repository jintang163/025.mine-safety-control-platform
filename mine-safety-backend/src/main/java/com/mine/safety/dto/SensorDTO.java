package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SensorDTO {

    private String sensorId;
    private String name;
    private String type;
    private String protocol;
    private String location;
    private BigDecimal coordinatesX;
    private BigDecimal coordinatesY;
    private BigDecimal coordinatesZ;
    private Integer samplingInterval;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String unit;
    private Integer status;
    private BigDecimal warningThreshold;
    private BigDecimal alarmThreshold;
    private BigDecimal currentValue;
    private String lastOnlineTime;
}
