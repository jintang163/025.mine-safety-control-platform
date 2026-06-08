package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报警记录实体类
 * 对应数据库表 alerts，存储所有报警事件的详细信息
 *
 * 包含信息：
 *   - 报警标识：编号、关联的传感器和规则
 *   - 报警内容：报警值、阈值、级别、描述
 *   - 处理状态：未处理/处理中/已处理/已忽略
 *   - 确认信息：确认人、确认时间、处理备注
 *   - 统计信息：首次报警时间、最后报警时间、报警次数
 */
@Data
@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alert_no", columnList = "alert_no"),
        @Index(name = "idx_sensor_id", columnList = "sensor_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_level", columnList = "level"),
        @Index(name = "idx_alert_time", columnList = "first_alert_time")
})
public class Alert {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 报警编号，业务主键
     * 格式：ALT + yyyyMMddHHmmss + 8位随机字符串
     */
    @Column(name = "alert_no", nullable = false, unique = true, length = 64)
    private String alertNo;

    /**
     * 关联的传感器ID
     */
    @Column(name = "sensor_id", nullable = false, length = 64)
    private String sensorId;

    /**
     * 传感器名称（冗余，便于查询）
     */
    @Column(name = "sensor_name", length = 128)
    private String sensorName;

    /**
     * 传感器类型（冗余，便于统计）
     */
    @Column(name = "sensor_type", length = 32)
    private String sensorType;

    /**
     * 报警位置（冗余，便于展示）
     */
    @Column(length = 256)
    private String location;

    /**
     * 触发报警时的传感器数值
     */
    @Column(name = "alert_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal alertValue;

    /**
     * 报警阈值（规则配置的阈值）
     */
    @Column(name = "threshold_value", precision = 12, scale = 4)
    private BigDecimal thresholdValue;

    /**
     * 报警级别
     * INFO(提示)、WARNING(预警)、ALERT(报警)、EMERGENCY(紧急)
     */
    @Column(nullable = false, length = 16)
    private String level;

    /**
     * 关联的报警规则ID
     */
    @Column(name = "rule_id")
    private Long ruleId;

    /**
     * 规则名称（冗余，便于展示）
     */
    @Column(name = "rule_name", length = 128)
    private String ruleName;

    /**
     * 报警描述
     */
    @Column(length = 512)
    private String description;

    /**
     * 处理状态
     * 0-未处理，1-处理中，2-已处理，3-已忽略
     */
    private Integer status = 0;

    /**
     * 确认人（处理人）
     */
    @Column(name = "acknowledged_by", length = 64)
    private String acknowledgedBy;

    /**
     * 确认时间
     */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /**
     * 处理备注
     */
    @Column(name = "acknowledged_comment", length = 512)
    private String acknowledgedComment;

    /**
     * 首次报警时间
     */
    @Column(name = "first_alert_time", nullable = false)
    private LocalDateTime firstAlertTime;

    /**
     * 最后报警时间（用于统计频率）
     */
    @Column(name = "last_alert_time", nullable = false)
    private LocalDateTime lastAlertTime;

    /**
     * 报警次数（同一规则重复触发时累加）
     */
    @Column(name = "alert_count")
    private Integer alertCount = 1;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 报警状态枚举
     */
    public enum AlertStatus {
        /** 未处理 */
        PENDING(0),
        /** 处理中 */
        PROCESSING(1),
        /** 已处理 */
        RESOLVED(2),
        /** 已忽略 */
        IGNORED(3);

        private final int value;

        AlertStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * 报警级别枚举
     */
    public enum AlertLevel {
        /** 提示信息 */
        INFO,
        /** 预警 */
        WARNING,
        /** 报警 */
        ALERT,
        /** 紧急报警 */
        EMERGENCY
    }
}
