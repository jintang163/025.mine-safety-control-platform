package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.config.MqttConfig;
import com.mine.safety.dto.SensorDataDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class MqttMessageListener implements MqttCallback {

    private final MqttConfig mqttConfig;
    private final KafkaProducerService kafkaProducerService;

    @Override
    public void connectionLost(Throwable cause) {
        log.error("MQTT连接丢失，原因: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.debug("收到MQTT消息 - 主题: {}, 消息: {}", topic, payload);

            if (topic.startsWith("mine/sensor/data")) {
                handleSensorData(payload);
            } else if (topic.startsWith("mine/alarm/")) {
                handleAlarmMessage(topic, payload);
            } else if (topic.startsWith("mine/command/response/")) {
                handleCommandResponse(topic, payload);
            }
        } catch (Exception e) {
            log.error("处理MQTT消息异常 - 主题: {}, 错误: {}", topic, e.getMessage(), e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("MQTT消息发送完成 - 消息ID: {}", token.getMessageId());
    }

    private void handleSensorData(String payload) {
        try {
            SensorDataDTO sensorDataDTO = JSON.parseObject(payload, SensorDataDTO.class);
            if (sensorDataDTO.getSensorId() == null) {
                log.warn("传感器数据缺少sensorId: {}", payload);
                return;
            }
            if (sensorDataDTO.getTimestamp() == null) {
                sensorDataDTO.setTimestamp(LocalDateTime.now());
            }

            kafkaProducerService.sendRawSensorData(sensorDataDTO);
        } catch (Exception e) {
            log.error("解析传感器数据失败: {}", e.getMessage(), e);
        }
    }

    private void handleAlarmMessage(String topic, String payload) {
        log.info("收到报警消息 - 主题: {}, 内容: {}", topic, payload);
    }

    private void handleCommandResponse(String topic, String payload) {
        log.debug("收到命令响应 - 主题: {}, 内容: {}", topic, payload);
    }

    public void publish(String topic, String payload, int qos) {
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            message.setRetained(false);
            mqttConfig.mqttClient(mqttConfig.mqttConnectOptions(), this).publish(topic, message);
        } catch (Exception e) {
            log.error("发送MQTT消息失败: {}", e.getMessage(), e);
        }
    }
}
