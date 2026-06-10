package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sensor_maintenance_records")
public class SensorMaintenanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("sensor_id")
    private String sensorId;

    @TableField("maintenance_no")
    private String maintenanceNo;

    @TableField("maintenance_type")
    private String maintenanceType;

    @TableField("maintenance_date")
    private LocalDateTime maintenanceDate;

    @TableField("maintenance_person")
    private String maintenancePerson;

    @TableField("maintenance_content")
    private String maintenanceContent;

    @TableField("replaced_parts")
    private String replacedParts;

    private BigDecimal cost;

    private String result;

    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum MaintenanceType {
        REPAIR,
        REPLACE,
        CLEAN,
        INSPECT,
        UPGRADE
    }

    public enum MaintenanceResult {
        COMPLETED,
        PARTIAL,
        FAILED
    }
}
