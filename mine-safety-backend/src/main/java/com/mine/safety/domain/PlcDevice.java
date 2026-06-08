package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "plc_devices")
public class PlcDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code", length = 64, nullable = false, unique = true)
    private String deviceCode;

    @Column(name = "device_name", length = 128, nullable = false)
    private String deviceName;

    @Column(name = "device_type", length = 32, nullable = false)
    private String deviceType;

    @Column(name = "protocol", length = 32, nullable = false)
    private String protocol;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "port")
    private Integer port;

    @Column(name = "slave_id")
    private Integer slaveId;

    @Column(name = "rack")
    private Integer rack;

    @Column(name = "slot")
    private Integer slot;

    @Column(name = "register_address", length = 64)
    private String registerAddress;

    @Column(name = "register_type", length = 32)
    private String registerType;

    @Column(name = "data_type", length = 32)
    private String dataType;

    @Column(name = "zone_code", length = 32)
    private String zoneCode;

    @Column(name = "location", length = 256)
    private String location;

    @Column(name = "on_value", length = 32)
    private String onValue;

    @Column(name = "off_value", length = 32)
    private String offValue;

    @Column(name = "status")
    private Integer status;

    @Column(name = "last_online_time")
    private LocalDateTime lastOnlineTime;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (enabled == null) {
            enabled = true;
        }
        if (slaveId == null) {
            slaveId = 1;
        }
        if (rack == null) {
            rack = 0;
        }
        if (slot == null) {
            slot = 1;
        }
        if (onValue == null) {
            onValue = "1";
        }
        if (offValue == null) {
            offValue = "0";
        }
        if (dataType == null) {
            dataType = "BOOL";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

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
