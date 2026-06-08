package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alert_no", columnList = "alert_no"),
        @Index(name = "idx_sensor_id", columnList = "sensor_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_level", columnList = "level"),
        @Index(name = "idx_alert_time", columnList = "first_alert_time")
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

    @Column(name = "alert_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal alertValue;

    @Column(name = "threshold_value", precision = 12, scale = 4)
    private BigDecimal thresholdValue;

    @Column(nullable = false, length = 16)
    private String level;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_name", length = 128)
    private String ruleName;

    @Column(length = 512)
    private String description;

    private Integer status = 0;

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
        PENDING(0), PROCESSING(1), RESOLVED(2), IGNORED(3);

        private final int value;

        AlertStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum AlertLevel {
        INFO, WARNING, ALERT, EMERGENCY
    }
}
