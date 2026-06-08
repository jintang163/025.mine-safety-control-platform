package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "alert_rules", indexes = {
        @Index(name = "idx_sensor_type", columnList = "sensor_type"),
        @Index(name = "idx_enabled", columnList = "enabled")
})
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 128)
    private String ruleName;

    @Column(name = "sensor_type", length = 32)
    private String sensorType;

    @Column(name = "sensor_id", length = 64)
    private String sensorId;

    @Column(name = "condition_type", nullable = false, length = 32)
    private String conditionType;

    @Column(name = "threshold_value", precision = 12, scale = 4)
    private BigDecimal thresholdValue;

    @Column(name = "threshold_value_max", precision = 12, scale = 4)
    private BigDecimal thresholdValueMax;

    private Integer duration = 0;

    @Column(nullable = false, length = 16)
    private String level;

    private Integer enabled = 1;

    @Column(name = "notification_channels", length = 512)
    private String notificationChannels;

    @Column(length = 512)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ConditionType {
        GT, GTE, LT, LTE, BETWEEN
    }

    public enum AlertLevel {
        INFO, WARNING, ALERT, EMERGENCY
    }

    public enum NotificationChannel {
        SMS, EMAIL, VOICE, WEBHOOK
    }
}
