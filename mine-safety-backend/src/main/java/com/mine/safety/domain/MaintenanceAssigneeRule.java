package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("maintenance_assignee_rules")
public class MaintenanceAssigneeRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("zone_code")
    private String zoneCode;

    @TableField("sensor_type")
    private String sensorType;

    @TableField("fault_type")
    private String faultType;

    @TableField("assignee")
    private String assignee;

    @TableField("assignee_phone")
    private String assigneePhone;

    @TableField("assignee_user_id")
    private String assigneeUserId;

    @TableField("priority")
    private Integer priority;

    @TableField("enabled")
    private Boolean enabled;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
