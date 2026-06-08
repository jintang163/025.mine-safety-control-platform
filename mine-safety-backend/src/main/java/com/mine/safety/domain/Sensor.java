package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sensors")
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_id", nullable = false, unique = true, length = 64)
    private String sensorId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 32)
    private String protocol;

    @Column(length = 256)
    private String location;

    @Column(name = "coordinates_x", precision = 10, scale = 6)
    private BigDecimal coordinatesX;

    @Column(name = "coordinates_y", precision = 10, scale = 6)
    private BigDecimal coordinatesY;

    @Column(name = "coordinates_z", precision = 10, scale = 6)
    private BigDecimal coordinatesZ;

    @Column(name = "sampling_interval")
    private Integer samplingInterval = 1;

    @Column(name = "min_value", precision = 10, scale = 4)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 10, scale = 4)
    private BigDecimal maxValue;

    @Column(length = 16)
    private String unit;

    private Integer status = 1;

    @Column(name = "last_online_time")
    private LocalDateTime lastOnlineTime;

    @Column(name = "warning_threshold", precision = 10, scale = 4)
    private BigDecimal warningThreshold;

    @Column(name = "alarm_threshold", precision = 10, scale = 4)
    private BigDecimal alarmThreshold;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum SensorType {
        GAS, DUST, CO, TEMPERATURE, WIND
    }

    public enum Protocol {
        MODBUS_RTU, MODBUS_TCP, OPC_UA, CAN, WIRELESS_4G, WIRELESS_5G
    }

    public enum Status {
        OFFLINE(0), ONLINE(1), FAULT(2);

        private final int value;

        Status(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
