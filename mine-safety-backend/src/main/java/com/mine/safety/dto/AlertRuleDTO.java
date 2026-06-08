package com.mine.safety.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import com.mine.safety.domain.AlertRuleDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRuleDTO {

    private Long id;

    private String ruleCode;

    private String ruleName;

    private String ruleType;

    private String sensorType;

    private String sensorId;

    private String zoneCode;

    private String droolsRule;

    private String groovyScript;

    private Map<String, Object> ruleParams;

    private String level;

    private String description;

    private Boolean enabled;

    private Integer version;

    private String createdBy;

    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private String updatedBy;

    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public static AlertRuleDTO fromEntity(AlertRuleDefinition entity) {
        if (entity == null) {
            return null;
        }
        return AlertRuleDTO.builder()
                .id(entity.getId())
                .ruleCode(entity.getRuleCode())
                .ruleName(entity.getRuleName())
                .ruleType(entity.getRuleType())
                .sensorType(entity.getSensorType())
                .sensorId(entity.getSensorId())
                .zoneCode(entity.getZoneCode())
                .droolsRule(entity.getDroolsRule())
                .groovyScript(entity.getGroovyScript())
                .ruleParams(entity.getRuleParams())
                .level(entity.getLevel())
                .description(entity.getDescription())
                .enabled(entity.getEnabled())
                .version(entity.getVersion())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public AlertRuleDefinition toEntity() {
        return AlertRuleDefinition.builder()
                .id(id)
                .ruleCode(ruleCode)
                .ruleName(ruleName)
                .ruleType(ruleType)
                .sensorType(sensorType)
                .sensorId(sensorId)
                .zoneCode(zoneCode)
                .droolsRule(droolsRule)
                .groovyScript(groovyScript)
                .ruleParams(ruleParams)
                .level(level)
                .description(description)
                .enabled(enabled)
                .version(version)
                .createdBy(createdBy)
                .updatedBy(updatedBy)
                .build();
    }
}
