package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_escalation_logs")
public class AlertEscalationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("alert_no")
    private String alertNo;

    @TableField("from_level")
    private String fromLevel;

    @TableField("to_level")
    private String toLevel;

    @TableField("escalation_reason")
    private String escalationReason;

    @TableField("notified_users")
    private String notifiedUsers;

    @TableField("notification_channels")
    private String notificationChannels;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
