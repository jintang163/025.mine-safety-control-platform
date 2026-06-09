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
@TableName("alert_rules")
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("rule_name")
    private String ruleName;

    @TableField("sensor_type")
    private String sensorType;

    @TableField("sensor_id")
    private String sensorId;

    @TableField("condition_type")
    private String conditionType;

    @TableField("threshold_value")
    private BigDecimal thresholdValue;

    @TableField("threshold_value_max")
    private BigDecimal thresholdValueMax;

    private Integer duration = 0;

    private String level;

    private Integer enabled = 1;

    @TableField("notification_channels")
    private String notificationChannels;

    private String description;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum ConditionType {
        GT,
        GTE,
        LT,
        LTE,
        BETWEEN
    }

    public enum AlertLevel {
        INFO,
        WARNING,
        ALERT,
        EMERGENCY
    }

    public enum NotificationChannel {
        SMS,
        EMAIL,
        VOICE,
        WEBHOOK
    }
}
