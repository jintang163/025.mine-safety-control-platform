package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sensor_data", indexes = {
        @Index(name = "idx_sensor_id", columnList = "sensor_id"),
        @Index(name = "idx_timestamp", columnList = "timestamp"),
        @Index(name = "idx_sensor_time", columnList = "sensor_id, timestamp")
})
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_id", nullable = false, length = 64)
    private String sensorId;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal value;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 256)
    private String location;

    private Integer quality = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Quality {
        ABNORMAL(0), NORMAL(1);

        private final int value;

        Quality(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
