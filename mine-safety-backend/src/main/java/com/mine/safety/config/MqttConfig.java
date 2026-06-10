package com.mine.safety.config;

import com.mine.safety.service.MqttMessageListener;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * MQTT客户端配置类
 * 负责与EMQX Broker建立连接，配置连接参数和订阅主题
 *
 * 主题规范：
 *   - 传感器数据上报：mine/sensor/data/{sensorId} （统一下发主题）
 *   - 报警通知：mine/alarm/{level}
 *   - 命令下发：mine/command/{sensorId}
 *   - 命令响应：mine/command/response/{sensorId}
 */
@Slf4j
@Data
@Configuration
public class MqttConfig {

    /**
     * MQTT Broker地址，如：tcp://localhost:1883
     */
    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    /**
     * 客户端ID，用于唯一标识连接
     */
    @Value("${mqtt.broker.client-id}")
    private String clientId;

    /**
     * 连接用户名
     */
    @Value("${mqtt.broker.username}")
    private String username;

    /**
     * 连接密码
     */
    @Value("${mqtt.broker.password}")
    private String password;

    /**
     * 连接超时时间（秒）
     */
    @Value("${mqtt.broker.connection-timeout}")
    private int connectionTimeout;

    /**
     * 心跳包间隔（秒）
     */
    @Value("${mqtt.broker.keep-alive-interval}")
    private int keepAliveInterval;

    /**
     * 是否自动重连
     */
    @Value("${mqtt.broker.auto-reconnect}")
    private boolean autoReconnect;

    /**
     * 是否清除会话（false表示持久会话）
     */
    @Value("${mqtt.broker.clean-session}")
    private boolean cleanSession;

    /**
     * 传感器数据主题前缀
     */
    @Value("${mqtt.topics.sensor-data}")
    private String sensorDataTopic;

    /**
     * 报警主题前缀
     */
    @Value("${mqtt.topics.alarm}")
    private String alarmTopic;

    /**
     * 命令主题前缀
     */
    @Value("${mqtt.topics.command:mine/command/#}")
    private String commandTopic;

    /**
     * 单例MqttClient实例，全局复用
     */
    private MqttClient mqttClientInstance;

    /**
     * 创建MQTT连接选项
     * 配置连接参数、安全认证、遗嘱消息等
     *
     * @return MqttConnectOptions连接选项
     */
    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        options.setAutomaticReconnect(autoReconnect);
        options.setCleanSession(cleanSession);
        // 设置最大未确认消息数，提升吞吐量
        options.setMaxInflight(1000);
        // 设置遗嘱消息，异常断开时通知
        try {
            String willPayload = String.format("{\"clientId\":\"%s\",\"status\":\"offline\",\"time\":%d}",
                    clientId, System.currentTimeMillis());
            options.setWill(
                    "mine/system/status/" + clientId,
                    willPayload.getBytes(),
                    1,
                    true
            );
        } catch (Exception e) {
            log.warn("设置MQTT遗嘱消息失败: {}", e.getMessage());
        }

        log.info("MQTT连接选项配置完成 - Broker: {}, ClientId: {}", brokerUrl, clientId);
        return options;
    }

    /**
     * 创建MqttClient单例Bean
     * 全局唯一的MQTT客户端实例，负责所有消息的发布和订阅
     *
     * @param options  连接选项
     * @param listener 消息监听器（回调）
     * @return MqttClient实例
     * @throws MqttException 连接失败时抛出
     */
    @Bean
    public MqttClient mqttClient(MqttConnectOptions options, MqttMessageListener listener) throws MqttException {
        if (mqttClientInstance != null && mqttClientInstance.isConnected()) {
            log.debug("复用已有的MQTT客户端连接");
            return mqttClientInstance;
        }

        // 创建客户端实例，使用内存持久化
        mqttClientInstance = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        // 设置消息回调监听器
        mqttClientInstance.setCallback(listener);

        // 建立连接
        log.info("正在连接MQTT Broker: {}", brokerUrl);
        mqttClientInstance.connect(options);
        log.info("MQTT Broker连接成功 - ClientId: {}", clientId);

        // 订阅主题 - 使用通配符订阅所有传感器数据主题
        // 订阅 mine/sensor/data/# 接收所有传感器上报的数据
        String sensorDataSubscribeTopic = sensorDataTopic.endsWith("/") ?
                sensorDataTopic + "#" : sensorDataTopic + "/#";
        mqttClientInstance.subscribe(sensorDataSubscribeTopic, 1);
        log.info("已订阅传感器数据主题: {} (QoS=1)", sensorDataSubscribeTopic);

        // 订阅报警主题
        mqttClientInstance.subscribe(alarmTopic, 1);
        log.info("已订阅报警主题: {} (QoS=1)", alarmTopic);

        // 订阅命令响应主题
        String commandResponseTopic = "mine/command/response/#";
        mqttClientInstance.subscribe(commandResponseTopic, 1);
        log.info("已订阅命令响应主题: {} (QoS=1)", commandResponseTopic);

        String shadowReportedTopic = "mine/shadow/reported/#";
        mqttClientInstance.subscribe(shadowReportedTopic, 1);
        log.info("已订阅设备影子上报主题: {} (QoS=1)", shadowReportedTopic);

        // 发布上线通知
        String onlinePayload = String.format("{\"clientId\":\"%s\",\"status\":\"online\",\"time\":%d}",
                clientId, System.currentTimeMillis());
        publish("mine/system/status/" + clientId, onlinePayload, 1, true);
        log.info("已发布客户端上线通知");

        return mqttClientInstance;
    }

    /**
     * 获取MQTT客户端实例
     * 用于其他类获取单例连接，避免重复创建连接
     *
     * @return MqttClient实例（已连接）
     */
    public synchronized MqttClient getMqttClient() {
        try {
            if (mqttClientInstance == null) {
                throw new IllegalStateException("MQTT客户端未初始化");
            }
            if (!mqttClientInstance.isConnected()) {
                log.warn("MQTT连接已断开，尝试重连...");
                mqttClientInstance.reconnect();
                log.info("MQTT连接已恢复");
            }
        } catch (MqttException e) {
            log.error("获取MQTT客户端失败: {}", e.getMessage(), e);
        }
        return mqttClientInstance;
    }

    /**
     * 发布MQTT消息（统一方法）
     * 复用单例连接，避免每次发布都创建新连接
     *
     * @param topic    主题
     * @param payload  消息内容（字符串）
     * @param qos      服务质量等级 (0, 1, 2)
     * @param retained 是否保留消息
     */
    public synchronized void publish(String topic, String payload, int qos, boolean retained) {
        try {
            MqttClient client = getMqttClient();
            if (client == null || !client.isConnected()) {
                log.error("MQTT客户端未连接，无法发布消息 - Topic: {}", topic);
                return;
            }

            org.eclipse.paho.client.mqttv3.MqttMessage message =
                    new org.eclipse.paho.client.mqttv3.MqttMessage(payload.getBytes());
            message.setQos(qos);
            message.setRetained(retained);

            client.publish(topic, message);
            log.debug("MQTT消息发布成功 - Topic: {}, QoS: {}, Retained: {}", topic, qos, retained);

        } catch (MqttException e) {
            log.error("MQTT消息发布失败 - Topic: {}, Error: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * 断开MQTT连接
     * 在应用关闭时调用，优雅断开
     */
    public synchronized void disconnect() {
        try {
            if (mqttClientInstance != null && mqttClientInstance.isConnected()) {
                // 发布离线通知
                String offlinePayload = String.format("{\"clientId\":\"%s\",\"status\":\"offline\",\"time\":%d}",
                        clientId, System.currentTimeMillis());
                publish("mine/system/status/" + clientId, offlinePayload, 1, true);

                mqttClientInstance.disconnect(3000);
                log.info("MQTT客户端已断开连接");
            }
        } catch (MqttException e) {
            log.error("断开MQTT连接失败: {}", e.getMessage(), e);
        }
    }
}
