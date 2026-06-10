package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("trend_alerts")
public class TrendAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("alert_no")
    private String alertNo;
    @TableField("rule_id")
    private Long ruleId;
    @TableField("rule_code")
    private String ruleCode;
    @TableField("rule_name")
    private String ruleName;
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
    @TableField("start_date")
    private LocalDate startDate;
    @TableField("end_date")
    private LocalDate endDate;
    @TableField("trend_data")
    private String trendData;
    private String description;
    private String severity;
    private Integer status;
    @TableField("acknowledged_by")
    private String acknowledgedBy;
    @TableField("acknowledged_at")
    private LocalDateTime acknowledgedAt;
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
