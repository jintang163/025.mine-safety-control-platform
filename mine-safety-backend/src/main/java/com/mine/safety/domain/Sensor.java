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
@TableName("sensors")
public class Sensor {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("sensor_id")
    private String sensorId;

    private String name;

    private String type;

    private String protocol;

    private String location;

    @TableField("coordinates_x")
    private BigDecimal coordinatesX;

    @TableField("coordinates_y")
    private BigDecimal coordinatesY;

    @TableField("coordinates_z")
    private BigDecimal coordinatesZ;

    @TableField("sampling_interval")
    private Integer samplingInterval = 1;

    @TableField("min_value")
    private BigDecimal minValue;

    @TableField("max_value")
    private BigDecimal maxValue;

    private String unit;

    private Integer status = 1;

    @TableField("last_online_time")
    private LocalDateTime lastOnlineTime;

    @TableField("warning_threshold")
    private BigDecimal warningThreshold;

    @TableField("alarm_threshold")
    private BigDecimal alarmThreshold;

    @TableField("power_off_threshold")
    private BigDecimal powerOffThreshold;

    @TableField("zone_code")
    private String zoneCode;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum SensorType {
        GAS,
        DUST,
        CO,
        TEMPERATURE,
        WIND
    }

    public enum Protocol {
        MODBUS_RTU,
        MODBUS_TCP,
        OPC_UA,
        CAN,
        WIRELESS_4G,
        WIRELESS_5G
    }

    public enum Status {
        OFFLINE(0),
        ONLINE(1),
        FAULT(2);

        private final int value;

        Status(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
