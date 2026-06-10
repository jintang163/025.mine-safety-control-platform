package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
import java.util.Map;

/**
 * MQTT消息监听器
 * 实现MqttCallback接口，处理所有MQTT消息的接收和发送
 *
 * 主题定义（统一规范）：
 *   订阅主题：
 *     - mine/sensor/data/#      所有传感器上报的数据
 *     - mine/alarm/#             报警通知消息
 *     - mine/command/response/#  命令响应消息
 *
 *   发布主题：
 *     - mine/sensor/data/{sensorId}    传感器数据（模拟器使用）
 *     - mine/alarm/{level}             报警事件
 *     - mine/command/{sensorId}        命令下发
 *     - mine/system/status/{clientId}  系统状态通知
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class MqttMessageListener implements MqttCallback {

    /**
     * MQTT配置类（提供单例连接和统一发布方法）
     */
    private final MqttConfig mqttConfig;

    /**
     * Kafka消息生产者服务，用于转发传感器数据到Kafka
     */
    private final KafkaProducerService kafkaProducerService;

    @Lazy
    private final DeviceShadowService deviceShadowService;

    /**
     * 连接丢失回调
     * 当MQTT连接意外断开时触发，MqttConfig会自动重连
     *
     * @param cause 连接丢失的原因
     */
    @Override
    public void connectionLost(Throwable cause) {
        log.error("MQTT连接丢失，原因: {}", cause.getMessage());
        // MqttConfig已配置automaticReconnect=true，会自动尝试重连
        // 这里仅记录日志，无需额外处理
    }

    /**
     * 消息到达回调
     * 处理所有订阅主题收到的消息，按主题路由到不同处理方法
     *
     * @param topic   消息主题
     * @param message 消息内容
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.debug("收到MQTT消息 - 主题: {}, QoS: {}, 消息长度: {}bytes",
                    topic, message.getQos(), payload.length());

            // 按主题前缀路由消息
            if (topic.startsWith("mine/sensor/data/")) {
                // 传感器数据：mine/sensor/data/{sensorId}
                handleSensorData(topic, payload);
            } else if (topic.startsWith("mine/alarm/")) {
                // 报警消息：mine/alarm/{level}
                handleAlarmMessage(topic, payload);
            } else if (topic.startsWith("mine/command/response/")) {
                // 命令响应：mine/command/response/{sensorId}
                handleCommandResponse(topic, payload);
            } else if (topic.startsWith("mine/system/status/")) {
                handleSystemStatus(topic, payload);
            } else if (topic.startsWith("mine/shadow/reported/")) {
                handleDeviceShadowReported(topic, payload);
            } else {
                log.debug("未匹配到主题处理器 - 主题: {}", topic);
            }

        } catch (Exception e) {
            log.error("处理MQTT消息异常 - 主题: {}, 错误: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * 消息发送完成回调
     * 当发布的消息被Broker确认后触发
     *
     * @param token 发送令牌，包含消息ID等信息
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        try {
            if (token.getMessage() != null) {
                log.debug("MQTT消息发送完成 - 消息ID: {}, 主题: {}",
                        token.getMessageId(),
                        token.getTopics() != null && token.getTopics().length > 0 ?
                                token.getTopics()[0] : "unknown");
            }
        } catch (Exception e) {
            log.debug("获取MQTT发送完成信息失败: {}", e.getMessage());
        }
    }

    /**
     * 处理传感器数据消息
     * 解析JSON格式的传感器数据，发送到Kafka进行后续处理
     *
     * @param topic   主题（包含sensorId）
     * @param payload 消息内容（JSON格式的SensorDataDTO）
     */
    private void handleSensorData(String topic, String payload) {
        try {
            // 从主题中提取sensorId
            // 主题格式: mine/sensor/data/{sensorId}
            String sensorId = extractSensorIdFromTopic(topic);

            // 解析JSON为DTO
            SensorDataDTO sensorDataDTO = JSON.parseObject(payload, SensorDataDTO.class);

            // 数据完整性校验
            if (sensorDataDTO.getSensorId() == null || sensorDataDTO.getSensorId().isEmpty()) {
                // 如果payload中没有sensorId，使用主题中提取的
                sensorDataDTO.setSensorId(sensorId);
            }

            // 如果时间戳为空，设置为当前时间
            if (sensorDataDTO.getTimestamp() == null) {
                sensorDataDTO.setTimestamp(LocalDateTime.now());
            }

            // 记录日志（DEBUG级别，生产环境可关闭）
            if (log.isTraceEnabled()) {
                log.trace("解析传感器数据 - 传感器: {}, 类型: {}, 值: {}{}",
                        sensorDataDTO.getSensorId(),
                        sensorDataDTO.getSensorType(),
                        sensorDataDTO.getValue(),
                        sensorDataDTO.getUnit() != null ? sensorDataDTO.getUnit() : "");
            }

            // 发送到Kafka原始数据Topic
            kafkaProducerService.sendRawSensorData(sensorDataDTO);

        } catch (Exception e) {
            log.error("解析传感器数据失败 - 主题: {}, 错误: {}", topic, e.getMessage(), e);
            log.debug("原始数据: {}", payload);
        }
    }

    /**
     * 处理报警消息
     * 可以接收来自其他系统的报警通知，做统一处理
     *
     * @param topic   主题
     * @param payload 消息内容
     */
    private void handleAlarmMessage(String topic, String payload) {
        try {
            // 提取报警级别
            String level = topic.startsWith("mine/alarm/") ?
                    topic.substring("mine/alarm/".length()) : "UNKNOWN";

            log.info("收到报警消息 - 级别: {}, 内容: {}", level, payload);

            // TODO: 可以在这里添加报警消息的处理逻辑
            // 例如：转发到报警平台、触发声光报警等

        } catch (Exception e) {
            log.error("处理报警消息失败 - 主题: {}, 错误: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * 处理命令响应消息
     * 当向传感器发送控制命令后，传感器的响应通过此主题返回
     *
     * @param topic   主题
     * @param payload 消息内容
     */
    private void handleCommandResponse(String topic, String payload) {
        try {
            // 提取sensorId
            String sensorId = topic.startsWith("mine/command/response/") ?
                    topic.substring("mine/command/response/".length()) : "UNKNOWN";

            log.debug("收到命令响应 - 传感器: {}, 内容: {}", sensorId, payload);

            // TODO: 可以在这里添加命令响应的处理逻辑
            // 例如：更新命令状态、记录操作日志等

        } catch (Exception e) {
            log.error("处理命令响应失败 - 主题: {}, 错误: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * 处理系统状态消息
     * 处理客户端上下线等系统通知
     *
     * @param topic   主题
     * @param payload 消息内容
     */
    private void handleSystemStatus(String topic, String payload) {
        try {
            String clientId = topic.startsWith("mine/system/status/") ?
                    topic.substring("mine/system/status/".length()) : "UNKNOWN";

            log.debug("收到系统状态通知 - 客户端: {}, 内容: {}", clientId, payload);

        } catch (Exception e) {
            log.error("处理系统状态消息失败: {}", e.getMessage());
        }
    }

    private void handleDeviceShadowReported(String topic, String payload) {
        try {
            String sensorId = topic.startsWith("mine/shadow/reported/") ?
                    topic.substring("mine/shadow/reported/".length()) : "UNKNOWN";

            log.debug("收到设备影子上报 - 传感器: {}, 内容: {}", sensorId, payload);

            JSONObject reported = JSON.parseObject(payload);
            if (reported != null && !reported.isEmpty()) {
                Map<String, Object> reportedState = reported.getInnerMap();
                deviceShadowService.handleReportedState(sensorId, reportedState);
            }
        } catch (Exception e) {
            log.error("处理设备影子上报失败 - 主题: {}, 错误: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * 从主题中提取sensorId
     * 主题格式：mine/sensor/data/{sensorId}
     *
     * @param topic 完整主题
     * @return sensorId
     */
    private String extractSensorIdFromTopic(String topic) {
        String prefix = "mine/sensor/data/";
        if (topic.startsWith(prefix)) {
            return topic.substring(prefix.length());
        }
        // 兼容不带前缀的情况
        return topic;
    }

    /**
     * 发布MQTT消息（统一入口）
     * 复用MqttConfig的单例连接，避免每次发布都创建新连接
     * 核心修复：原先每次调用都创建新的MqttClient，现在复用单例
     *
     * @param topic   主题
     * @param payload 消息内容
     * @param qos     服务质量等级 (0, 1, 2)
     */
    public void publish(String topic, String payload, int qos) {
        // 直接调用MqttConfig的统一发布方法
        // MqttConfig内部维护单例连接，自动处理重连
        mqttConfig.publish(topic, payload, qos, false);
    }

    /**
     * 发布MQTT消息（带retain选项）
     *
     * @param topic    主题
     * @param payload  消息内容
     * @param qos      服务质量等级
     * @param retained 是否保留消息
     */
    public void publish(String topic, String payload, int qos, boolean retained) {
        mqttConfig.publish(topic, payload, qos, retained);
    }

    /**
     * 发送命令到指定传感器
     *
     * @param sensorId 传感器ID
     * @param command  命令内容
     * @param qos      服务质量
     */
    public void sendCommand(String sensorId, String command, int qos) {
        String topic = "mine/command/" + sensorId;
        publish(topic, command, qos);
        log.info("已发送命令 - 传感器: {}, 命令: {}", sensorId, command);
    }

    /**
     * 发布报警事件到MQTT
     *
     * @param level   报警级别
     * @param payload 报警内容
     */
    public void publishAlarm(String level, String payload) {
        String topic = "mine/alarm/" + level.toLowerCase();
        publish(topic, payload, 1, true);
        log.warn("已发布报警事件 - 级别: {}, 内容: {}", level, payload);
    }
}
