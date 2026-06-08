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
@Table(name = "alert_rule_definitions")
public class AlertRuleDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", length = 64, nullable = false, unique = true)
    private String ruleCode;

    @Column(name = "rule_name", length = 128, nullable = false)
    private String ruleName;

    @Column(name = "rule_type", length = 32, nullable = false)
    private String ruleType;

    @Column(name = "sensor_type", length = 32)
    private String sensorType;

    @Column(name = "sensor_id", length = 64)
    private String sensorId;

    @Column(name = "zone_code", length = 32)
    private String zoneCode;

    @Column(name = "drools_rule", columnDefinition = "TEXT")
    private String droolsRule;

    @Column(name = "groovy_script", columnDefinition = "TEXT")
    private String groovyScript;

    @Column(name = "rule_params", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> ruleParams;

    @Column(name = "level", length = 16, nullable = false)
    private String level;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "version")
    private Integer version;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (enabled == null) {
            enabled = true;
        }
        if (version == null) {
            version = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RuleType {
        SINGLE_THRESHOLD, TREND, COMPOUND
    }
}
