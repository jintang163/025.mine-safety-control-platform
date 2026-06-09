package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "alert_disposal_records", indexes = {
        @Index(name = "idx_disposal_alert_no", columnList = "alert_no"),
        @Index(name = "idx_disposal_operator", columnList = "operator")
})
public class AlertDisposalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_no", nullable = false, length = 64)
    private String alertNo;

    @Column(name = "disposal_type", nullable = false, length = 32)
    private String disposalType;

    @Column(name = "disposal_measures", nullable = false, length = 1024)
    private String disposalMeasures;

    @Column(name = "image_urls", length = 2048)
    private String imageUrls;

    @Column(name = "operator", nullable = false, length = 64)
    private String operator;

    @Column(name = "operator_role", length = 32)
    private String operatorRole;

    @Column(name = "recovery_value", precision = 12, scale = 4)
    private java.math.BigDecimal recoveryValue;

    @Column(name = "recovery_time")
    private LocalDateTime recoveryTime;

    @Column(length = 512)
    private String remark;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum DisposalType {
        CONFIRM,
        PROCESS,
        RECOVER,
        CLOSE
    }
}
