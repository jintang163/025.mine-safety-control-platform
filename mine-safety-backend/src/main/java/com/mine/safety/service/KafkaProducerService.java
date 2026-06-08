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

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topic.sensor.raw:sensor-raw-data}")
    private String rawDataTopic;

    @Value("${kafka.topic.sensor.processed:sensor-processed-data}")
    private String processedDataTopic;

    @Value("${kafka.topic.alarm:alarm-events}")
    private String alarmTopic;

    public void sendRawSensorData(SensorDataDTO data) {
        String payload = JSON.toJSONString(data);
        send(rawDataTopic, data.getSensorId(), payload);
    }

    public void sendProcessedSensorData(SensorDataDTO data) {
        String payload = JSON.toJSONString(data);
        send(processedDataTopic, data.getSensorId(), payload);
    }

    public void sendAlertEvent(AlertDTO alert) {
        String payload = JSON.toJSONString(alert);
        send(alarmTopic, alert.getSensorId(), payload);
    }

    private void send(String topic, String key, String payload) {
        try {
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, payload);
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
