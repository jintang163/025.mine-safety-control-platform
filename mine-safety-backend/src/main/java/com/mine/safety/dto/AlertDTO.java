package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报警传输对象（DTO）
 * 用于报警信息在各层之间的传递，包括：
 *   - 报警事件通知（Kafka、MQTT）
 *   - 前端报警列表/详情展示
 *   - 报警处理操作
 *
 * 包含信息：
 *   - 报警标识：编号、关联的传感器和规则
 *   - 报警内容：报警值、阈值、级别、描述
 *   - 处理状态：未处理/处理中/已处理/已忽略
 *   - 确认信息：确认人、确认时间、处理备注
 *   - 统计信息：首次报警时间、最后报警时间、报警次数
 *   - 通知渠道：需要发送的通知渠道（仅内部使用）
 */
@Data
public class AlertDTO {

    /**
     * 报警编号，业务主键
     * 格式：ALT + yyyyMMddHHmmss + 8位随机字符串
     */
    private String alertNo;

    /**
     * 关联的传感器ID
     */
    private String sensorId;

    /**
     * 传感器名称
     */
    private String sensorName;

    /**
     * 传感器类型
     */
    private String sensorType;

    /**
     * 报警位置
     */
    private String location;

    /**
     * 触发报警时的传感器数值
     */
    private BigDecimal alertValue;

    /**
     * 报警阈值
     */
    private BigDecimal thresholdValue;

    /**
     * 报警级别
     * INFO(提示)、WARNING(预警)、ALERT(报警)、EMERGENCY(紧急)
     */
    private String level;

    /**
     * 关联的报警规则ID
     */
    private Long ruleId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 报警描述
     */
    private String description;

    /**
     * 处理状态
     * 0-未处理，1-处理中，2-已处理，3-已忽略
     */
    private Integer status;

    /**
     * 确认人（处理人）
     */
    private String acknowledgedBy;

    /**
     * 确认时间
     */
    private LocalDateTime acknowledgedAt;

    /**
     * 处理备注
     */
    private String acknowledgedComment;

    /**
     * 首次报警时间
     */
    private LocalDateTime firstAlertTime;

    /**
     * 最后报警时间
     */
    private LocalDateTime lastAlertTime;

    /**
     * 报警次数
     */
    private Integer alertCount;

    /**
     * 通知渠道（多个用逗号分隔）
     * 仅内部使用，用于processAlertNotification方法
     * 例如：SMS,EMAIL,VOICE,WEBHOOK
     */
    private String notificationChannels;
}
