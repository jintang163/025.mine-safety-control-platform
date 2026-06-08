package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ThresholdApprovalDTO {
    private Long id;
    private String approvalNo;
    private String sensorId;
    private String sensorName;
    private String sensorType;
    private String thresholdType;
    private BigDecimal oldValue;
    private BigDecimal newValue;
    private String applicant;
    private String applyReason;
    private Integer status;
    private String statusText;
    private String approver;
    private String approveComment;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}
