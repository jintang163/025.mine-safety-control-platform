package com.mine.simulator;

import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class SensorSimulator {

    public static void main(String[] args) {
        SpringApplication.run(SensorSimulator.class, args);
    }

    @Bean
    public MqttClient mqttClient(SimulatorConfig config) throws Exception {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{config.getMqtt().getBroker()});
        options.setUserName(config.getMqtt().getUsername());
        options.setPassword(config.getMqtt().getPassword().toCharArray());
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);

        String clientId = "sensor-simulator-" + UUID.randomUUID().toString().substring(0, 8);
        MqttClient client = new MqttClient(config.getMqtt().getBroker(), clientId, new MemoryPersistence());
        client.connect(options);
        log.info("MQTT客户端已连接: {}", config.getMqtt().getBroker());
        return client;
    }

    @Bean
    public CommandLineRunner run(SimulatorConfig config) {
        return args -> {
            log.info("传感器数据模拟器已启动");
            log.info("模拟传感器数量: {}", config.getSensors().size());
            log.info("发送间隔: {}ms", config.getIntervalMs());
            log.info("异常数据概率: {}%", config.getAnomalyRate() * 100);
        };
    }

    @Scheduled(fixedDelayString = "${simulator.interval-ms:1000}")
    public void generateAndSend(MqttClient client, SimulatorConfig config) {
        Random random = new Random();
        for (SimulatorConfig.SensorConfig sensor : config.getSensors()) {
            try {
                SensorData data = generateSensorData(sensor, config.getAnomalyRate(), random);
                String topic = config.getMqtt().getTopicPrefix() + "/" + data.getSensorId();
                String payload = JSON.toJSONString(data);

                MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                message.setQos(config.getMqtt().getQos());
                client.publish(topic, message);

                log.debug("发送数据 - 传感器: {}, 类型: {}, 值: {}, 质量: {}",
                        data.getSensorId(), data.getSensorType(), data.getValue(), data.getQuality());
            } catch (Exception e) {
                log.error("发送传感器数据失败: {}", e.getMessage());
            }
        }
    }

    private SensorData generateSensorData(SimulatorConfig.SensorConfig config, double anomalyRate, Random random) {
        SensorData data = new SensorData();
        data.setSensorId(config.getId());
        data.setSensorType(config.getType());
        data.setTimestamp(LocalDateTime.now());
        data.setQuality(1);
        data.setLocation(config.getLocation());
        data.setUnit(getUnitByType(config.getType()));

        double baseValue = config.getBaseValue();
        double variance = config.getVariance();
        double value = baseValue + (random.nextGaussian() * variance);

        if (random.nextDouble() < anomalyRate) {
            value = baseValue * (2 + random.nextDouble() * 3);
            data.setQuality(0);
            log.warn("生成异常数据 - 传感器: {}, 值: {}", config.getId(), value);
        }

        value = Math.max(0, value);
        data.setValue(BigDecimal.valueOf(Math.round(value * 1000.0) / 1000.0));

        return data;
    }

    private String getUnitByType(String type) {
        return switch (type) {
            case "GAS" -> "% CH4";
            case "DUST" -> "mg/m³";
            case "CO" -> "ppm";
            case "TEMPERATURE" -> "℃";
            case "WIND" -> "m/s";
            default -> "";
        };
    }

    @Data
    public static class SensorData {
        private String sensorId;
        private String sensorType;
        private BigDecimal value;
        private LocalDateTime timestamp;
        private String location;
        private Double coordinatesX;
        private Double coordinatesY;
        private Double coordinatesZ;
        private String unit;
        private Integer quality;
        private String protocol;
    }

    @Data
    @Configuration
    @ConfigurationProperties(prefix = "simulator")
    public static class SimulatorConfig {
        private int intervalMs = 1000;
        private double anomalyRate = 0.02;
        private MqttConfig mqtt = new MqttConfig();
        private List<SensorConfig> sensors = new ArrayList<>();

        @Data
        public static class MqttConfig {
            private String broker = "tcp://localhost:1883";
            private String username = "admin";
            private String password = "public";
            private String topicPrefix = "mine/sensor/data";
            private int qos = 1;
        }

        @Data
        public static class SensorConfig {
            private String id;
            private String type;
            private String location;
            private double baseValue;
            private double variance;
        }
    }
}
