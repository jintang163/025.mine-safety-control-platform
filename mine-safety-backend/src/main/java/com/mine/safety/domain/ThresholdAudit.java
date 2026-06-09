package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("threshold_audit")
public class ThresholdAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("sensor_id")
    private String sensorId;

    @TableField("threshold_type")
    private String thresholdType;

    @TableField("old_value")
    private BigDecimal oldValue;

    @TableField("new_value")
    private BigDecimal newValue;

    private String operator;

    @TableField("operation_type")
    private String operationType;

    @TableField("approval_id")
    private Long approvalId;

    @TableField("change_reason")
    private String changeReason;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public enum ThresholdType {
        WARNING,
        ALARM,
        POWER_OFF
    }

    public enum OperationType {
        CREATE,
        UPDATE,
        APPROVE,
        REJECT
    }
}
