package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "alert_rule_action_relations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"rule_id", "action_id"}))
public class RuleActionRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "rule_code", length = 64, nullable = false)
    private String ruleCode;

    @Column(name = "action_id", nullable = false)
    private Long actionId;

    @Column(name = "action_code", length = 64, nullable = false)
    private String actionCode;

    @Column(name = "execution_order")
    private Integer executionOrder;

    @Column(name = "delay_seconds")
    private Integer delaySeconds;

    @Column(name = "condition_expression", length = 512)
    private String conditionExpression;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (executionOrder == null) {
            executionOrder = 0;
        }
        if (delaySeconds == null) {
            delaySeconds = 0;
        }
    }
}
