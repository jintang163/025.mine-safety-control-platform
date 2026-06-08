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

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final SensorDataService sensorDataService;
    private final AlertService alertService;
    private final KafkaProducerService kafkaProducerService;

    @Value("${app.data-processing.enabled:true}")
    private boolean dataProcessingEnabled;

    @Value("${app.alert.enabled:true}")
    private boolean alertEnabled;

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
                    SensorDataDTO dto = JSON.parseObject(message, SensorDataDTO.class);
                    if (dto == null || dto.getSensorId() == null) {
                        log.warn("无效的传感器数据: {}", message);
                        continue;
                    }

                    if (dataProcessingEnabled) {
                        dto = sensorDataService.processSensorData(dto);
                    }

                    kafkaProducerService.sendProcessedSensorData(dto);

                    if (alertEnabled && dto.getQuality() == 1) {
                        alertService.checkAndTriggerAlert(dto);
                    }
                } catch (Exception e) {
                    log.error("处理传感器数据失败: {}", e.getMessage());
                }
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("消费Kafka消息失败 - 主题: {}, 组: {}, 错误: {}", topic, groupId, e.getMessage());
            ack.nack(1000);
        }
    }

    @KafkaListener(topics = "${kafka.topic.alarm:alarm-events}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeAlertEvents(@Payload List<String> messages,
                               Acknowledgment ack) {
        try {
            for (String message : messages) {
                try {
                    AlertDTO alert = JSON.parseObject(message, AlertDTO.class);
                    if (alert != null) {
                        alertService.processAlertNotification(alert);
                    }
                } catch (Exception e) {
                    log.error("处理报警事件失败: {}", e.getMessage());
                }
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("消费报警事件失败: {}", e.getMessage());
            ack.nack(1000);
        }
    }
}
