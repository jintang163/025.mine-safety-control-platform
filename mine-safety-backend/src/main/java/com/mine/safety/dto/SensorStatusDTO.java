package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SensorStatusDTO {

    private String sensorId;
    private String name;
    private String type;
    private String location;
    private String zoneCode;
    private Integer status;
    private String statusText;
    private Integer batteryLevel;
    private String batteryStatus;
    private Integer signalStrength;
    private String signalQuality;
    private Integer dataUploadDelay;
    private String delayLevel;
    private String lastOnlineTime;
    private BigDecimal currentValue;
    private String unit;
}
