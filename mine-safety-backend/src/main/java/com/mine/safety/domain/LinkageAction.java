package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "linkage_actions")
public class LinkageAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_code", length = 64, nullable = false, unique = true)
    private String actionCode;

    @Column(name = "action_name", length = 128, nullable = false)
    private String actionName;

    @Column(name = "action_type", length = 32, nullable = false)
    private String actionType;

    @Column(name = "target_type", length = 32)
    private String targetType;

    @Column(name = "target_code", length = 64)
    private String targetCode;

    @Column(name = "action_params", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> actionParams;

    @Column(name = "execution_mode", length = 16)
    private String executionMode;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "max_retry")
    private Integer maxRetry;

    @Column(name = "retry_interval_seconds")
    private Integer retryIntervalSeconds;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (enabled == null) {
            enabled = true;
        }
        if (executionMode == null) {
            executionMode = "PARALLEL";
        }
        if (priority == null) {
            priority = 0;
        }
        if (timeoutSeconds == null) {
            timeoutSeconds = 30;
        }
        if (maxRetry == null) {
            maxRetry = 3;
        }
        if (retryIntervalSeconds == null) {
            retryIntervalSeconds = 5;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ActionType {
        SOUND_LIGHT_ALARM, VOICE_BROADCAST, REMOTE_POWER_OFF, MESSAGE_PUSH, VIDEO_POPUP
    }

    public enum TargetType {
        ZONE, SENSOR, DEVICE, USER, ROLE
    }

    public enum ExecutionMode {
        SERIAL, PARALLEL
    }
}
