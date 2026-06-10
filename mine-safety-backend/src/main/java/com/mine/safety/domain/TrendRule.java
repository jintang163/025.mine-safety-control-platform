package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("trend_rules")
public class TrendRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("rule_code")
    private String ruleCode;
    @TableField("rule_name")
    private String ruleName;
    private String description;
    @TableField("sensor_type")
    private String sensorType;
    @TableField("zone_code")
    private String zoneCode;
    private String metric;
    @TableField("trend_direction")
    private String trendDirection;
    @TableField("consecutive_periods")
    private Integer consecutivePeriods;
    @TableField("period_unit")
    private String periodUnit;
    @TableField("threshold_value")
    private BigDecimal thresholdValue;
    private String severity;
    @TableField("notification_channels")
    private String notificationChannels;
    private Boolean enabled;
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
