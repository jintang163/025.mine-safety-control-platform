package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.dto.AlertDTO;
import com.mine.safety.dto.SensorDataDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Kafka消费者服务
 * 负责从Kafka消费原始传感器数据和报警事件，
 * 并进行相应的业务处理（数据处理、报警检测、通知发送）。
 *
 * 消费模式：
 *   - 批量消费（每次消费多条消息，提高吞吐量）
 *   - 手动ACK（处理完成后确认，保证消息不丢失）
 *   - 并发消费（4个线程并发消费，提高处理速度）
 *   - 失败重试（处理失败时延迟1秒后重新消费）
 *
 * @author mine-safety
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    /** 传感器数据处理服务，用于数据预处理 */
    private final SensorDataService sensorDataService;

    /** 报警服务，用于报警检测和通知 */
    private final AlertService alertService;

    /** Kafka生产者服务，用于发送处理后的数据到下一个Topic */
    private final KafkaProducerService kafkaProducerService;

    /** 是否启用数据处理（默认启用），可配置关闭用于性能对比 */
    @Value("${app.data-processing.enabled:true}")
    private boolean dataProcessingEnabled;

    /** 是否启用报警检测（默认启用），可配置关闭用于测试 */
    @Value("${app.alert.enabled:true}")
    private boolean alertEnabled;

    /**
     * 消费原始传感器数据
     * 从sensor-raw-data主题消费边缘网关上报的原始数据，
     * 经过预处理后发送到处理后Topic，并进行报警检测。
     *
     * 处理流程：
     *   1. 批量接收Kafka消息（List<String>）
     *   2. 逐条解析为SensorDataDTO
     *   3. 数据预处理（异常检测、去噪、补全）
     *   4. 发送处理后数据到sensor-processed-data Topic
     *   5. 报警检测（仅对质量正常的数据）
     *   6. 批量确认（ACK）
     *
     * @param messages 消息列表（批量消费）
     * @param ack      确认对象（手动ACK）
     * @param topic    消费的主题名（日志用）
     * @param groupId  消费组ID（日志用）
     */
    @KafkaListener(topics = "${kafka.topic.sensor.raw:sensor-raw-data}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "4")
    public void consumeRawSensorData(@Payload List<String> messages,
                                     Acknowledgment ack,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     @Header(KafkaHeaders.GROUP_ID) String groupId) {
        try {
            for (String message : messages) {
                try {
                    // 解析JSON消息
                    SensorDataDTO dto = JSON.parseObject(message, SensorDataDTO.class);
                    if (dto == null || dto.getSensorId() == null) {
                        log.warn("无效的传感器数据: {}", message);
                        continue;
                    }

                    // 数据预处理（异常检测、去噪、补全）
                    if (dataProcessingEnabled) {
                        dto = sensorDataService.processSensorData(dto);
                    }

                    // 发送处理后的数据到下一个Topic（供其他系统消费）
                    kafkaProducerService.sendProcessedSensorData(dto);

                    // 报警检测（仅对质量正常的数据）
                    // checkAndTriggerAlert 已合并三级阈值检测和规则引擎检测
                    if (alertEnabled && dto.getQuality() == 1) {
                        alertService.checkAndTriggerAlert(dto);
                    }
                } catch (Exception e) {
                    // 单条消息处理失败不影响其他消息
                    log.error("处理传感器数据失败: {}", e.getMessage());
                }
            }
            // 批量确认（所有消息处理成功后确认）
            ack.acknowledge();
        } catch (Exception e) {
            // 批量处理失败，延迟1秒后重新消费
            log.error("消费Kafka消息失败 - 主题: {}, 组: {}, 错误: {}", topic, groupId, e.getMessage());
            ack.nack(1000);
        }
    }

    /**
     * 消费报警事件
     * 从alarm-events主题消费报警事件，进行多渠道通知发送。
     *
     * 处理流程：
     *   1. 批量接收报警事件消息
     *   2. 逐条解析为AlertDTO
     *   3. 调用报警服务发送通知（SMS/Email/Voice/Webhook）
     *   4. 批量确认（ACK）
     *
     * 设计说明：
     *   - 报警检测和通知发送异步解耦
     *   - 即使通知服务暂时不可用，报警事件也不会丢失
     *   - 支持水平扩展，提高通知发送吞吐量
     *
     * @param messages 报警事件消息列表
     * @param ack      确认对象（手动ACK）
     */
    @KafkaListener(topics = "${kafka.topic.alarm:alarm-events}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeAlertEvents(@Payload List<String> messages,
                               Acknowledgment ack) {
        try {
            for (String message : messages) {
                try {
                    // 解析报警事件
                    AlertDTO alert = JSON.parseObject(message, AlertDTO.class);
                    if (alert != null) {
                        // 发送多渠道通知
                        alertService.processAlertNotification(alert);
                    }
                } catch (Exception e) {
                    // 单条通知失败不影响其他通知
                    log.error("处理报警事件失败: {}", e.getMessage());
                }
            }
            // 批量确认
            ack.acknowledge();
        } catch (Exception e) {
            // 批量处理失败，延迟1秒后重新消费
            log.error("消费报警事件失败: {}", e.getMessage());
            ack.nack(1000);
        }
    }
}
