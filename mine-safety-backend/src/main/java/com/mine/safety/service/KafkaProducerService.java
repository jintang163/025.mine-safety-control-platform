package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.dto.AlertDTO;
import com.mine.safety.dto.SensorDataDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka生产者服务
 * 封装Kafka消息发送操作，支持异步发送和回调确认。
 *
 * 主要发送的Topic：
 *   - sensor-raw-data: 原始传感器数据（来自MQTT/边缘网关）
 *   - sensor-processed-data: 处理后的传感器数据（已去噪、异常检测）
 *   - alarm-events: 报警事件（触发报警时发送）
 *
 * 发送策略：
 *   - 异步发送（非阻塞，提高性能）
 *   - 消息key使用sensorId，保证同一传感器的数据顺序性
 *   - 发送结果回调（记录成功/失败日志）
 *   - 失败自动重试（由KafkaTemplate配置）
 *
 * @author mine-safety
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    /** Kafka模板，用于发送消息 */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** 原始传感器数据Topic（可配置） */
    @Value("${kafka.topic.sensor.raw:sensor-raw-data}")
    private String rawDataTopic;

    /** 处理后传感器数据Topic（可配置） */
    @Value("${kafka.topic.sensor.processed:sensor-processed-data}")
    private String processedDataTopic;

    /** 报警事件Topic（可配置） */
    @Value("${kafka.topic.alarm:alarm-events}")
    private String alarmTopic;

    /**
     * 发送原始传感器数据
     * 由MQTT消费者在接收到边缘网关数据时调用。
     *
     * @param data 原始传感器数据
     */
    public void sendRawSensorData(SensorDataDTO data) {
        String payload = JSON.toJSONString(data);
        send(rawDataTopic, data.getSensorId(), payload);
    }

    /**
     * 发送处理后的传感器数据
     * 由Kafka消费者在数据处理完成后调用，供其他系统消费。
     *
     * @param data 处理后的传感器数据
     */
    public void sendProcessedSensorData(SensorDataDTO data) {
        String payload = JSON.toJSONString(data);
        send(processedDataTopic, data.getSensorId(), payload);
    }

    /**
     * 发送报警事件
     * 由报警服务在检测到报警时调用，供通知消费者发送通知。
     *
     * @param alert 报警事件
     */
    public void sendAlertEvent(AlertDTO alert) {
        String payload = JSON.toJSONString(alert);
        send(alarmTopic, alert.getSensorId(), payload);
    }

    /**
     * 通用发送方法
     * 异步发送消息到指定Topic，并注册回调处理发送结果。
     *
     * 设计说明：
     *   - 使用sensorId作为消息key，保证同一传感器的数据发送到同一分区，
     *     从而保证消息的顺序性（同一传感器的数据按接收顺序处理）
     *   - 异步发送，不阻塞业务线程
     *   - 回调中记录成功/失败日志，便于问题排查
     *
     * @param topic   目标Topic
     * @param key     消息key（用于分区路由）
     * @param payload 消息内容（JSON字符串）
     */
    private void send(String topic, String key, String payload) {
        try {
            // 异步发送消息
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, payload);

            // 注册发送结果回调
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("发送Kafka消息失败 - 主题: {}, 错误: {}", topic, ex.getMessage());
                } else {
                    log.debug("发送Kafka消息成功 - 主题: {}, 分区: {}, 偏移量: {}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            log.error("发送Kafka消息异常: {}", e.getMessage(), e);
        }
    }
}
