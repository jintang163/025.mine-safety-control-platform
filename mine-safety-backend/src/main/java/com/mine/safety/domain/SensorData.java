package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 传感器历史数据实体类
 * 对应数据库表 sensor_data，存储传感器采集的历史数据
 *
 * 注意：
 *   - 近期热数据主要存储在InfluxDB时序数据库中
 *   - MySQL仅存储需要复杂查询或长期归档的数据
 *   - 建议定期清理或归档历史数据
 */
@Data
@Entity
@Table(name = "sensor_data", indexes = {
        @Index(name = "idx_sensor_id", columnList = "sensor_id"),
        @Index(name = "idx_timestamp", columnList = "timestamp"),
        @Index(name = "idx_sensor_time", columnList = "sensor_id, timestamp")
})
public class SensorData {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 传感器ID
     */
    @Column(name = "sensor_id", nullable = false, length = 64)
    private String sensorId;

    /**
     * 采集数值
     */
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal value;

    /**
     * 采集时间戳
     */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * 采集位置（冗余，便于查询）
     */
    @Column(length = 256)
    private String location;

    /**
     * 数据质量
     * 0-异常（可能存在问题），1-正常
     */
    private Integer quality = 1;

    /**
     * 入库时间
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 数据质量枚举
     */
    public enum Quality {
        /** 异常数据 */
        ABNORMAL(0),
        /** 正常数据 */
        NORMAL(1);

        private final int value;

        Quality(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
