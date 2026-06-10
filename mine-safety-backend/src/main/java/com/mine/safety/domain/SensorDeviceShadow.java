package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sensor_device_shadow")
public class SensorDeviceShadow {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("sensor_id")
    private String sensorId;

    @TableField("reported_state")
    private String reportedState;

    @TableField("desired_state")
    private String desiredState;

    @TableField("reported_version")
    private Integer reportedVersion;

    @TableField("desired_version")
    private Integer desiredVersion;

    @TableField("last_reported_time")
    private LocalDateTime lastReportedTime;

    @TableField("last_desired_time")
    private LocalDateTime lastDesiredTime;

    @TableField("sync_status")
    private String syncStatus;

    @TableField("delta")
    private String delta;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum SyncStatus {
        SYNCED,
        PENDING,
        FAILED
    }
}
