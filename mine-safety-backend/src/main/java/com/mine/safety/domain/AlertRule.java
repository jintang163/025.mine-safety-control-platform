package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报警规则实体类
 * 对应数据库表 alert_rules，定义所有报警触发规则
 *
 * 规则匹配逻辑：
 *   - sensor_type 为空：匹配所有类型的传感器
 *   - sensor_id 为空：匹配所有传感器
 *   - 两者都为空：全局规则，应用于所有传感器
 *   - 两者都指定：仅应用于特定传感器
 *
 * 核心属性：
 *   - 条件类型：大于、大于等于、小于、小于等于、区间
 *   - 持续时间：条件需要持续满足的时间（秒）
 *   - 报警级别：INFO/WARNING/ALERT/EMERGENCY
 *   - 通知渠道：短信/邮件/语音/Webhook
 */
@Data
@Entity
@Table(name = "alert_rules", indexes = {
        @Index(name = "idx_sensor_type", columnList = "sensor_type"),
        @Index(name = "idx_enabled", columnList = "enabled")
})
public class AlertRule {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 规则名称
     */
    @Column(name = "rule_name", nullable = false, length = 128)
    private String ruleName;

    /**
     * 适用的传感器类型（为空表示所有类型）
     * 例如：GAS、DUST、CO、TEMPERATURE、WIND
     */
    @Column(name = "sensor_type", length = 32)
    private String sensorType;

    /**
     * 适用的传感器ID（为空表示所有传感器）
     * 用于针对特定传感器的特殊规则
     */
    @Column(name = "sensor_id", length = 64)
    private String sensorId;

    /**
     * 条件类型
     * GT(大于)、GTE(大于等于)、LT(小于)、LTE(小于等于)、BETWEEN(区间)
     */
    @Column(name = "condition_type", nullable = false, length = 32)
    private String conditionType;

    /**
     * 阈值（下限）
     */
    @Column(name = "threshold_value", precision = 12, scale = 4)
    private BigDecimal thresholdValue;

    /**
     * 阈值上限（仅用于BETWEEN条件）
     */
    @Column(name = "threshold_value_max", precision = 12, scale = 4)
    private BigDecimal thresholdValueMax;

    /**
     * 持续时间（秒）
     * 条件需要持续满足的时间，0表示立即触发
     * 例如：5秒表示连续5秒满足条件才触发报警
     */
    private Integer duration = 0;

    /**
     * 报警级别
     * INFO(提示)、WARNING(预警)、ALERT(报警)、EMERGENCY(紧急)
     */
    @Column(nullable = false, length = 16)
    private String level;

    /**
     * 是否启用
     * 0-禁用，1-启用
     */
    private Integer enabled = 1;

    /**
     * 通知渠道（多个用逗号分隔）
     * 例如：SMS,EMAIL,VOICE,WEBHOOK
     */
    @Column(name = "notification_channels", length = 512)
    private String notificationChannels;

    /**
     * 规则描述
     */
    @Column(length = 512)
    private String description;

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
     * 条件类型枚举
     */
    public enum ConditionType {
        /** 大于 */
        GT,
        /** 大于等于 */
        GTE,
        /** 小于 */
        LT,
        /** 小于等于 */
        LTE,
        /** 区间（介于两者之间） */
        BETWEEN
    }

    /**
     * 报警级别枚举
     */
    public enum AlertLevel {
        /** 提示 */
        INFO,
        /** 预警 */
        WARNING,
        /** 报警 */
        ALERT,
        /** 紧急报警 */
        EMERGENCY
    }

    /**
     * 通知渠道枚举
     */
    public enum NotificationChannel {
        /** 短信 */
        SMS,
        /** 邮件 */
        EMAIL,
        /** 语音 */
        VOICE,
        /** Webhook回调 */
        WEBHOOK
    }
}
