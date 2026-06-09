package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "alert_escalation_logs", indexes = {
        @Index(name = "idx_esca_alert_no", columnList = "alert_no"),
        @Index(name = "idx_esca_level", columnList = "from_level")
})
public class AlertEscalationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_no", nullable = false, length = 64)
    private String alertNo;

    @Column(name = "from_level", nullable = false, length = 16)
    private String fromLevel;

    @Column(name = "to_level", nullable = false, length = 16)
    private String toLevel;

    @Column(name = "escalation_reason", length = 256)
    private String escalationReason;

    @Column(name = "notified_users", length = 512)
    private String notifiedUsers;

    @Column(name = "notification_channels", length = 256)
    private String notificationChannels;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
