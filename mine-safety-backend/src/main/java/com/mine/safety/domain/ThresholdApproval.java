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
@TableName("threshold_approval")
public class ThresholdApproval {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("approval_no")
    private String approvalNo;

    @TableField("sensor_id")
    private String sensorId;

    @TableField("threshold_type")
    private String thresholdType;

    @TableField("old_value")
    private BigDecimal oldValue;

    @TableField("new_value")
    private BigDecimal newValue;

    private String applicant;

    @TableField("apply_reason")
    private String applyReason;

    private Integer status = 0;

    private String approver;

    @TableField("approve_comment")
    private String approveComment;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum ApprovalStatus {
        PENDING(0),
        APPROVED(1),
        REJECTED(2);

        private final int value;

        ApprovalStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum ThresholdType {
        WARNING,
        ALARM,
        POWER_OFF
    }
}
