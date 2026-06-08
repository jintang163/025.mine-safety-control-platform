package com.mine.safety.config;

import com.mine.safety.service.MqttMessageListener;
import lombok.Data;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

@Data
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.broker.client-id}")
    private String clientId;

    @Value("${mqtt.broker.username}")
    private String username;

    @Value("${mqtt.broker.password}")
    private String password;

    @Value("${mqtt.broker.connection-timeout}")
    private int connectionTimeout;

    @Value("${mqtt.broker.keep-alive-interval}")
    private int keepAliveInterval;

    @Value("${mqtt.broker.auto-reconnect}")
    private boolean autoReconnect;

    @Value("${mqtt.broker.clean-session}")
    private boolean cleanSession;

    @Value("${mqtt.topics.sensor-data}")
    private String sensorDataTopic;

    @Value("${mqtt.topics.alarm}")
    private String alarmTopic;

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
        options.setMaxInflight(1000);
        return options;
    }

    @Bean
    public MqttClient mqttClient(MqttConnectOptions options, MqttMessageListener listener) throws Exception {
        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        client.setCallback(listener);
        client.connect(options);
        client.subscribe(sensorDataTopic, 1);
        client.subscribe(alarmTopic, 1);
        return client;
    }
}
