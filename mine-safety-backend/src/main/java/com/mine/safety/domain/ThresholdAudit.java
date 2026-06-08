package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "threshold_audit", indexes = {
        @Index(name = "idx_sensor_id", columnList = "sensor_id"),
        @Index(name = "idx_threshold_type", columnList = "threshold_type"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
public class ThresholdAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_id", nullable = false, length = 64)
    private String sensorId;

    @Column(name = "threshold_type", nullable = false, length = 32)
    private String thresholdType;

    @Column(name = "old_value", precision = 10, scale = 4)
    private BigDecimal oldValue;

    @Column(name = "new_value", nullable = false, precision = 10, scale = 4)
    private BigDecimal newValue;

    @Column(nullable = false, length = 64)
    private String operator;

    @Column(name = "operation_type", nullable = false, length = 32)
    private String operationType;

    @Column(name = "approval_id")
    private Long approvalId;

    @Column(name = "change_reason", length = 512)
    private String changeReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
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
