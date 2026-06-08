package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AlertDTO {

    private String alertNo;
    private String sensorId;
    private String sensorName;
    private String sensorType;
    private String location;
    private BigDecimal alertValue;
    private BigDecimal thresholdValue;
    private String level;
    private Long ruleId;
    private String ruleName;
    private String description;
    private Integer status;
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private String acknowledgedComment;
    private LocalDateTime firstAlertTime;
    private LocalDateTime lastAlertTime;
    private Integer alertCount;
    private String notificationChannels;
}
