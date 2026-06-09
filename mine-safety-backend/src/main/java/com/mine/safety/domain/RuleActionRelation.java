package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("alert_rule_action_relations")
public class RuleActionRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("rule_id")
    private Long ruleId;

    @TableField("rule_code")
    private String ruleCode;

    @TableField("action_id")
    private Long actionId;

    @TableField("action_code")
    private String actionCode;

    @TableField("execution_order")
    private Integer executionOrder = 0;

    @TableField("delay_seconds")
    private Integer delaySeconds = 0;

    @TableField("condition_expression")
    private String conditionExpression;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
