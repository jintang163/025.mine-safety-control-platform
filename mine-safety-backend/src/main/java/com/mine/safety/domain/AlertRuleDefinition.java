package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "alert_rule_definitions", autoResultMap = true)
public class AlertRuleDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("rule_code")
    private String ruleCode;

    @TableField("rule_name")
    private String ruleName;

    @TableField("rule_type")
    private String ruleType;

    @TableField("sensor_type")
    private String sensorType;

    @TableField("sensor_id")
    private String sensorId;

    @TableField("zone_code")
    private String zoneCode;

    @TableField("drools_rule")
    private String droolsRule;

    @TableField("groovy_script")
    private String groovyScript;

    @TableField(value = "rule_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> ruleParams;

    @TableField("level")
    private String level;

    @TableField("description")
    private String description;

    @TableField("enabled")
    private Boolean enabled = true;

    @TableField("version")
    private Integer version = 1;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField("updated_by")
    private String updatedBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum RuleType {
        SINGLE_THRESHOLD, TREND, COMPOUND
    }
}
