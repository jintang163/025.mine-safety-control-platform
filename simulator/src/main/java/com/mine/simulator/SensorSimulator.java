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

/**
 * 煤矿传感器数据模拟器
 * 独立运行的模拟数据生成工具，用于在没有真实传感器设备时
 * 模拟井下各种传感器（瓦斯、粉尘、CO、温度、风速）的数据，
 * 并通过MQTT协议发送到EMQX Broker。
 *
 * 主要功能：
 *   - 模拟多种传感器类型（GAS/DUST/CO/TEMPERATURE/WIND）
 *   - 按配置的基准值和方差生成真实的随机数据
 *   - 支持按指定概率生成异常数据（用于测试报警功能）
 *   - 可配置发送间隔、MQTT连接参数、传感器参数
 *   - 定时发送数据到MQTT主题：mine/sensor/data/{sensorId}
 *
 * 使用场景：
 *   - 开发测试：在没有真实设备时进行功能开发和测试
 *   - 压力测试：模拟大量传感器数据进行系统性能测试
 *   - 演示展示：用于产品演示和功能展示
 *
 * @author mine-safety
 * @since 1.0.0
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
public class SensorSimulator {

    /**
     * 应用程序入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SensorSimulator.class, args);
    }

    /**
     * 创建MQTT客户端Bean
     * 配置连接参数（超时、心跳、自动重连等），建立与EMQX Broker的连接。
     *
     * 客户端ID使用随机UUID，确保多个模拟器实例可以同时运行而不冲突。
     *
     * @param config 模拟器配置（包含MQTT连接参数）
     * @return MQTT客户端实例
     * @throws Exception 连接失败时抛出异常
     */
    @Bean
    public MqttClient mqttClient(SimulatorConfig config) throws Exception {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{config.getMqtt().getBroker()});
        options.setUserName(config.getMqtt().getUsername());
        options.setPassword(config.getMqtt().getPassword().toCharArray());
        options.setConnectionTimeout(30);       // 连接超时30秒
        options.setKeepAliveInterval(60);       // 心跳间隔60秒
        options.setAutomaticReconnect(true);    // 自动重连
        options.setCleanSession(true);          // 清除会话

        // 使用随机客户端ID，支持多实例运行
        String clientId = "sensor-simulator-" + UUID.randomUUID().toString().substring(0, 8);
        MqttClient client = new MqttClient(config.getMqtt().getBroker(), clientId, new MemoryPersistence());
        client.connect(options);
        log.info("MQTT客户端已连接: {}", config.getMqtt().getBroker());
        return client;
    }

    /**
     * 应用启动完成后执行的初始化逻辑
     * 打印模拟器配置信息，便于确认配置是否正确。
     *
     * @param config 模拟器配置
     * @return CommandLineRunner实例
     */
    @Bean
    public CommandLineRunner run(SimulatorConfig config) {
        return args -> {
            log.info("传感器数据模拟器已启动");
            log.info("模拟传感器数量: {}", config.getSensors().size());
            log.info("发送间隔: {}ms", config.getIntervalMs());
            log.info("异常数据概率: {}%", config.getAnomalyRate() * 100);
        };
    }

    /**
     * 定时生成并发送传感器数据
     * 按配置的间隔时间（默认1000ms）循环执行，为每个配置的传感器
     * 生成模拟数据并发送到MQTT Broker。
     *
     * 发送主题格式：{topicPrefix}/{sensorId}
     * 例如：mine/sensor/data/GAS-001
     *
     * @param client MQTT客户端
     * @param config 模拟器配置
     */
    @Scheduled(fixedDelayString = "${simulator.interval-ms:1000}")
    public void generateAndSend(MqttClient client, SimulatorConfig config) {
        Random random = new Random();
        for (SimulatorConfig.SensorConfig sensor : config.getSensors()) {
            try {
                // 1. 生成模拟数据
                SensorData data = generateSensorData(sensor, config.getAnomalyRate(), random);

                // 2. 构造MQTT主题和消息
                String topic = config.getMqtt().getTopicPrefix() + "/" + data.getSensorId();
                String payload = JSON.toJSONString(data);

                // 3. 发送MQTT消息
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

    /**
     * 生成单个传感器的模拟数据
     * 使用正态分布生成接近真实场景的随机数据，并按概率生成异常数据。
     *
     * 数据生成算法：
     *   - 正常值：value = baseValue + (高斯随机数 × variance)
     *   - 异常值：value = baseValue × (2 + 随机数 × 3)，即2~5倍基准值
     *   - 数值下限：所有值不小于0
     *   - 精度：保留3位小数
     *
     * @param config      传感器配置（基准值、方差等）
     * @param anomalyRate 异常数据概率（0~1）
     * @param random      随机数生成器（复用以提高性能）
     * @return 模拟传感器数据
     */
    private SensorData generateSensorData(SimulatorConfig.SensorConfig config, double anomalyRate, Random random) {
        SensorData data = new SensorData();
        data.setSensorId(config.getId());
        data.setSensorType(config.getType());
        data.setTimestamp(LocalDateTime.now());
        data.setQuality(1);  // 默认为正常质量
        data.setLocation(config.getLocation());
        data.setUnit(getUnitByType(config.getType()));

        // 生成基于正态分布的随机值
        double baseValue = config.getBaseValue();
        double variance = config.getVariance();
        double value = baseValue + (random.nextGaussian() * variance);

        // 按概率生成异常数据（2~5倍基准值）
        if (random.nextDouble() < anomalyRate) {
            value = baseValue * (2 + random.nextDouble() * 3);
            data.setQuality(0);  // 标记为异常质量
            log.warn("生成异常数据 - 传感器: {}, 值: {}", config.getId(), value);
        }

        // 确保数值非负，并保留3位小数
        value = Math.max(0, value);
        data.setValue(BigDecimal.valueOf(Math.round(value * 1000.0) / 1000.0));

        return data;
    }

    /**
     * 根据传感器类型获取单位
     *
     * @param type 传感器类型
     * @return 单位字符串
     */
    private String getUnitByType(String type) {
        return switch (type) {
            case "GAS" -> "% CH4";        // 瓦斯：甲烷体积百分比
            case "DUST" -> "mg/m³";       // 粉尘：毫克每立方米
            case "CO" -> "ppm";           // 一氧化碳：百万分比浓度
            case "TEMPERATURE" -> "℃";    // 温度：摄氏度
            case "WIND" -> "m/s";         // 风速：米每秒
            default -> "";
        };
    }

    /**
     * 模拟传感器数据DTO
     * 与后端SensorDataDTO保持一致的数据结构，确保数据格式兼容。
     */
    @Data
    public static class SensorData {
        /** 传感器ID */
        private String sensorId;
        /** 传感器类型（GAS/DUST/CO/TEMPERATURE/WIND） */
        private String sensorType;
        /** 测量值 */
        private BigDecimal value;
        /** 数据时间戳 */
        private LocalDateTime timestamp;
        /** 位置（巷道/工作面名称） */
        private String location;
        /** X坐标（预留） */
        private Double coordinatesX;
        /** Y坐标（预留） */
        private Double coordinatesY;
        /** Z坐标（预留） */
        private Double coordinatesZ;
        /** 单位 */
        private String unit;
        /** 数据质量（0-异常，1-正常） */
        private Integer quality;
        /** 通讯协议（预留） */
        private String protocol;
    }

    /**
     * 模拟器配置类
     * 从application.yml中读取以simulator为前缀的配置项。
     */
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "simulator")
    public static class SimulatorConfig {
        /** 发送间隔（毫秒），默认1000ms */
        private int intervalMs = 1000;
        /** 异常数据概率（0~1），默认2% */
        private double anomalyRate = 0.02;
        /** MQTT配置 */
        private MqttConfig mqtt = new MqttConfig();
        /** 模拟传感器列表 */
        private List<SensorConfig> sensors = new ArrayList<>();

        /**
         * MQTT连接配置
         */
        @Data
        public static class MqttConfig {
            /** Broker地址，默认tcp://localhost:1883 */
            private String broker = "tcp://localhost:1883";
            /** 用户名，默认admin */
            private String username = "admin";
            /** 密码，默认public */
            private String password = "public";
            /** 主题前缀，默认mine/sensor/data */
            private String topicPrefix = "mine/sensor/data";
            /** QoS等级（0/1/2），默认1 */
            private int qos = 1;
        }

        /**
         * 单个传感器配置
         */
        @Data
        public static class SensorConfig {
            /** 传感器ID */
            private String id;
            /** 传感器类型 */
            private String type;
            /** 安装位置 */
            private String location;
            /** 基准值（正常值中心） */
            private double baseValue;
            /** 方差（数据波动范围） */
            private double variance;
        }
    }
}
