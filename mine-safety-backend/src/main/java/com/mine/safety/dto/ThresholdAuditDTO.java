package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ThresholdAuditDTO {
    private Long id;
    private String sensorId;
    private String sensorName;
    private String thresholdType;
    private String thresholdTypeText;
    private BigDecimal oldValue;
    private BigDecimal newValue;
    private String operator;
    private String operationType;
    private String operationTypeText;
    private Long approvalId;
    private String changeReason;
    private LocalDateTime createdAt;
}
