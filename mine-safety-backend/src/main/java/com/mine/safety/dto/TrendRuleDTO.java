package com.mine.safety.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TrendRuleDTO {
    private Long id;
    private String ruleCode;
    private String ruleName;
    private String description;
    private String sensorType;
    private String zoneCode;
    private String metric;
    private String trendDirection;
    private Integer consecutivePeriods;
    private String periodUnit;
    private BigDecimal thresholdValue;
    private String severity;
    private String notificationChannels;
    private Boolean enabled;
}
