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
@TableName("alerts")
public class Alert {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("alert_no")
    private String alertNo;

    @TableField("sensor_id")
    private String sensorId;

    @TableField("sensor_name")
    private String sensorName;

    @TableField("sensor_type")
    private String sensorType;

    private String location;

    @TableField("tunnel")
    private String tunnel;

    @TableField("alert_value")
    private BigDecimal alertValue;

    @TableField("threshold_value")
    private BigDecimal thresholdValue;

    @TableField("threshold_type")
    private String thresholdType;

    private String level;

    @TableField("rule_id")
    private Long ruleId;

    @TableField("rule_name")
    private String ruleName;

    private String description;

    private Integer status = 0;

    @TableField("escalation_level")
    private String escalationLevel;

    @TableField("escalation_time")
    private LocalDateTime escalationTime;

    @TableField("confirmed_by")
    private String confirmedBy;

    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;

    @TableField("processing_by")
    private String processingBy;

    @TableField("processing_at")
    private LocalDateTime processingAt;

    @TableField("recovered_at")
    private LocalDateTime recoveredAt;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("acknowledged_by")
    private String acknowledgedBy;

    @TableField("acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @TableField("acknowledged_comment")
    private String acknowledgedComment;

    @TableField("first_alert_time")
    private LocalDateTime firstAlertTime;

    @TableField("last_alert_time")
    private LocalDateTime lastAlertTime;

    @TableField("alert_count")
    private Integer alertCount = 1;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum AlertStatus {
        PENDING(0),
        PROCESSING(1),
        RESOLVED(2),
        IGNORED(3),
        CONFIRMED(4),
        RECOVERED(5),
        CLOSED(6);

        private final int value;

        AlertStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static AlertStatus fromValue(int value) {
            for (AlertStatus s : values()) {
                if (s.value == value) return s;
            }
            throw new IllegalArgumentException("Unknown AlertStatus value: " + value);
        }
    }

    public enum EscalationLevel {
        DUTY("DUTY"),
        SHIFT_LEADER("SHIFT_LEADER"),
        MINE_MANAGER("MINE_MANAGER");

        private final String value;

        EscalationLevel(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public EscalationLevel next() {
            return switch (this) {
                case DUTY -> SHIFT_LEADER;
                case SHIFT_LEADER -> MINE_MANAGER;
                case MINE_MANAGER -> MINE_MANAGER;
            };
        }
    }

    public enum AlertLevel {
        INFO,
        WARNING,
        ALERT,
        EMERGENCY
    }
}
