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
@TableName("plc_devices")
public class PlcDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("device_code")
    private String deviceCode;

    @TableField("device_name")
    private String deviceName;

    @TableField("device_type")
    private String deviceType;

    @TableField("protocol")
    private String protocol;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("port")
    private Integer port;

    @TableField("slave_id")
    private Integer slaveId = 1;

    @TableField("rack")
    private Integer rack = 0;

    @TableField("slot")
    private Integer slot = 1;

    @TableField("register_address")
    private String registerAddress;

    @TableField("register_type")
    private String registerType;

    @TableField("data_type")
    private String dataType = "BOOL";

    @TableField("zone_code")
    private String zoneCode;

    @TableField("location")
    private String location;

    @TableField("on_value")
    private String onValue = "1";

    @TableField("off_value")
    private String offValue = "0";

    @TableField("status")
    private Integer status;

    @TableField("last_online_time")
    private LocalDateTime lastOnlineTime;

    @TableField("enabled")
    private Boolean enabled = true;

    @TableField("description")
    private String description;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum DeviceType {
        PLC_SOUND_LIGHT, PLC_BROADCAST, PLC_POWER_CONTROL, PLC_OTHER
    }

    public enum Protocol {
        MODBUS_TCP, OPC_UA, S7, MODBUS_RTU
    }

    public enum RegisterType {
        COIL, HOLDING_REGISTER, INPUT_REGISTER
    }

    public enum DataType {
        BOOL, INT16, INT32, FLOAT
    }
}
