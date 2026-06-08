package com.mine.safety.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ThresholdApplyDTO {
    @NotBlank(message = "传感器ID不能为空")
    private String sensorId;

    @NotBlank(message = "阈值类型不能为空")
    private String thresholdType;

    @NotNull(message = "新阈值不能为空")
    private BigDecimal newValue;

    @NotBlank(message = "申请人不能为空")
    private String applicant;

    private String applyReason;
}
