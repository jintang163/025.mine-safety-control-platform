package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_fault_orders")
public class DeviceFaultOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("sensor_id")
    private String sensorId;

    @TableField("sensor_name")
    private String sensorName;

    @TableField("fault_type")
    private String faultType;

    @TableField("fault_level")
    private String faultLevel;

    @TableField("fault_description")
    private String faultDescription;

    @TableField("fault_time")
    private LocalDateTime faultTime;

    private String location;

    @TableField("zone_code")
    private String zoneCode;

    private String assignee;

    @TableField("assignee_phone")
    private String assigneePhone;

    private Integer status;

    private String resolution;

    @TableField("resolution_time")
    private LocalDateTime resolutionTime;

    @TableField("resolved_by")
    private String resolvedBy;

    @TableField("notify_channels")
    private String notifyChannels;

    @TableField("notify_status")
    private Integer notifyStatus;

    @TableField("notify_time")
    private LocalDateTime notifyTime;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum FaultType {
        OFFLINE,
        LOW_BATTERY,
        SIGNAL_WEAK,
        DATA_ABNORMAL,
        CALIBRATION_EXPIRED
    }

    public enum FaultLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum OrderStatus {
        PENDING(0),
        PROCESSING(1),
        COMPLETED(2),
        CLOSED(3);

        private final int value;

        OrderStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
