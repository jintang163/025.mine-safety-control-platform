package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报警记录实体类
 * 对应数据库表 alerts，存储所有报警事件的详细信息
 *
 * 包含信息：
 *   - 报警标识：编号、关联的传感器和规则
 *   - 报警内容：报警值、阈值、级别、描述
 *   - 处理状态：未处理/处理中/已处理/已忽略
 *   - 确认信息：确认人、确认时间、处理备注
 *   - 统计信息：首次报警时间、最后报警时间、报警次数
 */
@Data
@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alert_no", columnList = "alert_no"),
        @Index(name = "idx_sensor_id", columnList = "sensor_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_level", columnList = "level"),
        @Index(name = "idx_alert_time", columnList = "first_alert_time"),
        @Index(name = "idx_escalation_level", columnList = "escalation_level"),
        @Index(name = "idx_tunnel", columnList = "tunnel"),
        @Index(name = "idx_threshold_type", columnList = "threshold_type")
})
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_no", nullable = false, unique = true, length = 64)
    private String alertNo;

    @Column(name = "sensor_id", nullable = false, length = 64)
    private String sensorId;

    @Column(name = "sensor_name", length = 128)
    private String sensorName;

    @Column(name = "sensor_type", length = 32)
    private String sensorType;

    @Column(length = 256)
    private String location;

    @Column(name = "tunnel", length = 128)
    private String tunnel;

    @Column(name = "alert_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal alertValue;

    @Column(name = "threshold_value", precision = 12, scale = 4)
    private BigDecimal thresholdValue;

    @Column(name = "threshold_type", length = 32)
    private String thresholdType;

    @Column(nullable = false, length = 16)
    private String level;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_name", length = 128)
    private String ruleName;

    @Column(length = 512)
    private String description;

    private Integer status = 0;

    @Column(name = "escalation_level", length = 16)
    private String escalationLevel;

    @Column(name = "escalation_time")
    private LocalDateTime escalationTime;

    @Column(name = "confirmed_by", length = 64)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "processing_by", length = 64)
    private String processingBy;

    @Column(name = "processing_at")
    private LocalDateTime processingAt;

    @Column(name = "recovered_at")
    private LocalDateTime recoveredAt;

    @Column(name = "closed_by", length = 64)
    private String closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "acknowledged_by", length = 64)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_comment", length = 512)
    private String acknowledgedComment;

    @Column(name = "first_alert_time", nullable = false)
    private LocalDateTime firstAlertTime;

    @Column(name = "last_alert_time", nullable = false)
    private LocalDateTime lastAlertTime;

    @Column(name = "alert_count")
    private Integer alertCount = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
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
