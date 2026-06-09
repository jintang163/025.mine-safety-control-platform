package com.mine.safety.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.mine.safety.domain.Sensor;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.repository.SensorRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 传感器数据模拟器服务
 * 支持按传感器独立配置的采样频率（sampling_interval）生成模拟数据
 * 核心功能：
 *   1. 从数据库读取传感器配置，包含sampling_interval
 *   2. 为每个传感器创建独立的定时任务
 *   3. 按各自频率发送数据（瓦斯1s、粉尘5s、CO 2s、温度5s、风速10s）
 *   4. 支持动态启停和配置刷新
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSimulatorService {

    /**
     * 传感器数据Repository，用于读取传感器配置
     */
    private final SensorRepository sensorRepository;

    /**
     * Kafka消息生产者服务
     */
    private final KafkaProducerService kafkaProducerService;

    /**
     * MQTT消息监听器（用于发布MQTT消息）
     */
    private final MqttMessageListener mqttMessageListener;

    /**
     * 模拟器总开关配置
     */
    @Value("${app.simulator.enabled:false}")
    private boolean simulatorEnabled;

    /**
     * 模拟器传感器配置（可选，用于覆盖数据库配置）
     */
    @Value("${app.simulator.sensors:}")
    private List<SimulatorSensorConfig> sensorConfigs;

    /**
     * MQTT主题前缀
     */
    @Value("${mqtt.topics.sensor-data:mine/sensor/data}")
    private String mqttTopicPrefix;

    /**
     * 发送到MQTT的比例（0-1），其余发送到Kafka
     */
    @Value("${app.simulator.mqtt-ratio:0.8}")
    private double mqttRatio;

    /**
     * 异常数据概率（0-1）
     */
    @Value("${app.simulator.anomaly-probability:0.02}")
    private double anomalyProbability;

    /**
     * 模拟器运行状态
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 随机数生成器
     */
    private final Random random = new Random();

    /**
     * 定时任务线程池，每个传感器一个线程
     */
    private ScheduledExecutorService scheduler;

    /**
     * 存储每个传感器的定时任务Future，用于动态启停
     * key: sensorId
     */
    private final Map<String, ScheduledFuture<?>> sensorTasks = new ConcurrentHashMap<>();

    /**
     * 存储每个传感器的配置（包含采样间隔）
     * key: sensorId
     */
    private final Map<String, SensorSimulatorConfig> sensorConfigMap = new ConcurrentHashMap<>();

    /**
     * 初始化方法
     * 创建线程池，加载传感器配置
     */
    @PostConstruct
    public void init() {
        // 创建可调度线程池，大小为传感器数量+2（预留）
        scheduler = Executors.newScheduledThreadPool(20);
        log.info("数据模拟器初始化完成");
    }

    /**
     * 应用启动完成后自动启动模拟器（如果配置启用）
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startSimulator() {
        if (simulatorEnabled) {
            log.info("传感器数据模拟器自动启动");
            start();
        }
    }

    /**
     * 销毁方法，优雅关闭线程池
     */
    @PreDestroy
    public void destroy() {
        stop();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        log.info("数据模拟器已销毁");
    }

    /**
     * 启动所有传感器模拟器
     * 1. 从数据库读取所有传感器配置
     * 2. 为每个传感器创建独立的定时任务
     * 3. 按各自的sampling_interval执行
     */
    public synchronized void start() {
        if (running.get()) {
            log.warn("模拟器已在运行中");
            return;
        }

        running.set(true);
        log.info("==================== 传感器数据模拟器启动 ====================");

        // 加载传感器配置
        loadSensorConfigs();

        // 为每个传感器创建独立的定时任务
        for (Map.Entry<String, SensorSimulatorConfig> entry : sensorConfigMap.entrySet()) {
            String sensorId = entry.getKey();
            SensorSimulatorConfig config = entry.getValue();

            scheduleSensorTask(sensorId, config);
        }

        log.info("共启动 {} 个传感器模拟任务", sensorConfigMap.size());
        log.info("============================================================");
    }

    /**
     * 停止所有传感器模拟器
     */
    public synchronized void stop() {
        if (!running.get()) {
            log.warn("模拟器未在运行");
            return;
        }

        running.set(false);

        // 取消所有定时任务
        for (Map.Entry<String, ScheduledFuture<?>> entry : sensorTasks.entrySet()) {
            entry.getValue().cancel(false);
            log.info("已停止传感器任务: {}", entry.getKey());
        }
        sensorTasks.clear();

        log.info("==================== 传感器数据模拟器已停止 ====================");
    }

    /**
     * 刷新传感器配置并重启任务
     * 当传感器配置变更时调用
     */
    public synchronized void refresh() {
        log.info("刷新传感器模拟配置...");

        // 取消所有现有任务
        for (Map.Entry<String, ScheduledFuture<?>> entry : sensorTasks.entrySet()) {
            entry.getValue().cancel(false);
        }
        sensorTasks.clear();
        sensorConfigMap.clear();

        // 重新加载配置并创建任务
        loadSensorConfigs();
        for (Map.Entry<String, SensorSimulatorConfig> entry : sensorConfigMap.entrySet()) {
            scheduleSensorTask(entry.getKey(), entry.getValue());
        }

        log.info("配置刷新完成，共 {} 个传感器任务", sensorConfigMap.size());
    }

    /**
     * 加载传感器配置
     * 优先使用YAML配置，其次使用数据库配置
     */
    private void loadSensorConfigs() {
        // 1. 优先从YAML配置加载
        if (sensorConfigs != null && !sensorConfigs.isEmpty()) {
            for (SimulatorSensorConfig yamlConfig : sensorConfigs) {
                SensorSimulatorConfig config = new SensorSimulatorConfig();
                config.setId(yamlConfig.getId());
                config.setType(yamlConfig.getType());
                config.setBaseValue(yamlConfig.getBaseValue());
                config.setVariance(yamlConfig.getVariance());

                // 从数据库读取采样间隔，如果不存在则根据类型使用默认值
                Sensor sensor = sensorRepository.selectOne(
                        new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, yamlConfig.getId()));
                if (sensor != null && sensor.getSamplingInterval() != null) {
                    config.setSamplingInterval(sensor.getSamplingInterval());
                    config.setName(sensor.getName());
                    config.setLocation(sensor.getLocation());
                    config.setUnit(sensor.getUnit());
                } else {
                    config.setSamplingInterval(getDefaultIntervalByType(yamlConfig.getType()));
                    config.setUnit(getUnitByType(yamlConfig.getType()));
                }

                sensorConfigMap.put(config.getId(), config);
                log.info("加载传感器配置 [YAML] - ID: {}, 类型: {}, 采样间隔: {}s",
                        config.getId(), config.getType(), config.getSamplingInterval());
            }
            return;
        }

        // 2. 从数据库加载所有传感器
        List<Sensor> sensors = sensorRepository.selectList(null);
        for (Sensor sensor : sensors) {
            // 只处理启用的传感器
            if (sensor.getStatus() == null || sensor.getStatus() == 0) {
                continue;
            }

            SensorSimulatorConfig config = new SensorSimulatorConfig();
            config.setId(sensor.getSensorId());
            config.setName(sensor.getName());
            config.setType(sensor.getType());
            config.setLocation(sensor.getLocation());
            config.setUnit(sensor.getUnit());

            // 使用数据库配置的采样间隔，如果未配置则使用类型默认值
            if (sensor.getSamplingInterval() != null && sensor.getSamplingInterval() > 0) {
                config.setSamplingInterval(sensor.getSamplingInterval());
            } else {
                config.setSamplingInterval(getDefaultIntervalByType(sensor.getType()));
            }

            // 根据类型设置基准值和方差
            config.setBaseValue(getBaseValueByType(sensor.getType()));
            config.setVariance(getVarianceByType(sensor.getType()));

            sensorConfigMap.put(config.getId(), config);
            log.info("加载传感器配置 [DB] - ID: {}, 名称: {}, 类型: {}, 采样间隔: {}s",
                    config.getId(), config.getName(), config.getType(), config.getSamplingInterval());
        }
    }

    /**
     * 为单个传感器创建定时任务
     *
     * @param sensorId 传感器ID
     * @param config   传感器配置
     */
    private void scheduleSensorTask(String sensorId, SensorSimulatorConfig config) {
        // 采样间隔（秒）
        int intervalSeconds = config.getSamplingInterval();

        // 创建定时任务，以固定延迟执行
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                () -> generateAndSendSensorData(config),
                random.nextInt(intervalSeconds),  // 初始延迟，错开启动时间避免并发峰值
                intervalSeconds,                   // 固定延迟
                TimeUnit.SECONDS
        );

        sensorTasks.put(sensorId, future);

        log.info("已创建传感器定时任务 - ID: {}, 采样频率: {}秒/次", sensorId, intervalSeconds);
    }

    /**
     * 生成并发送传感器数据
     * 每个传感器定时任务的执行逻辑
     *
     * @param config 传感器配置
     */
    private void generateAndSendSensorData(SensorSimulatorConfig config) {
        if (!running.get()) return;

        try {
            // 生成模拟数据
            SensorDataDTO data = generateSensorData(config);

            // 按比例发送到MQTT或Kafka
            if (random.nextDouble() < mqttRatio) {
                sendToMqtt(data);
            } else {
                sendToKafka(data);
            }

            log.trace("生成模拟数据 - 传感器: {} ({}), 值: {}{}, 频率: {}s",
                    config.getName(), config.getId(), data.getValue(),
                    config.getUnit(), config.getSamplingInterval());

        } catch (Exception e) {
            log.error("生成模拟数据失败 - 传感器: {}, 错误: {}", config.getId(), e.getMessage(), e);
        }
    }

    /**
     * 生成传感器模拟数据
     *
     * @param config 传感器配置
     * @return 传感器数据DTO
     */
    private SensorDataDTO generateSensorData(SensorSimulatorConfig config) {
        SensorDataDTO dto = new SensorDataDTO();
        dto.setSensorId(config.getId());
        dto.setSensorType(config.getType());
        dto.setTimestamp(LocalDateTime.now());
        dto.setLocation(config.getLocation());
        dto.setUnit(config.getUnit());
        dto.setQuality(1);  // 默认数据质量正常
        dto.setProtocol("SIMULATOR");

        // 基于基准值和方差生成数据（高斯分布）
        double baseValue = config.getBaseValue();
        double variance = config.getVariance();
        double value = baseValue + (random.nextGaussian() * variance);

        // 按概率生成异常数据
        if (random.nextDouble() < anomalyProbability) {
            // 异常值：基准值的1.5-3倍
            value = baseValue * (1.5 + random.nextDouble() * 2);
            dto.setQuality(0);  // 标记数据质量异常
            log.debug("生成异常数据 - 传感器: {}, 正常值范围: {}-{}, 异常值: {}",
                    config.getId(),
                    String.format("%.2f", baseValue - variance),
                    String.format("%.2f", baseValue + variance),
                    String.format("%.2f", value));
        }

        // 确保值非负
        value = Math.max(0, value);

        dto.setValue(BigDecimal.valueOf(value).setScale(4, BigDecimal.ROUND_HALF_UP));

        return dto;
    }

    /**
     * 发送数据到MQTT
     * 主题格式：mine/sensor/data/{sensorId}
     *
     * @param data 传感器数据
     */
    private void sendToMqtt(SensorDataDTO data) {
        // 统一主题格式：mine/sensor/data/{sensorId}
        String topic = mqttTopicPrefix + "/" + data.getSensorId();
        String payload = JSON.toJSONString(data);
        mqttMessageListener.publish(topic, payload, 1);
    }

    /**
     * 发送数据到Kafka
     *
     * @param data 传感器数据
     */
    private void sendToKafka(SensorDataDTO data) {
        kafkaProducerService.sendRawSensorData(data);
    }

    /**
     * 根据传感器类型获取默认采样间隔
     * 预置标准：
     *   - 瓦斯(GAS): 1秒
     *   - 粉尘(DUST): 5秒
     *   - 一氧化碳(CO): 2秒
     *   - 温度(TEMPERATURE): 5秒
     *   - 风速(WIND): 10秒
     *
     * @param type 传感器类型
     * @return 默认采样间隔（秒）
     */
    private int getDefaultIntervalByType(String type) {
        return switch (type) {
            case "GAS" -> 1;         // 瓦斯：1秒/次
            case "CO" -> 2;          // 一氧化碳：2秒/次
            case "DUST" -> 5;        // 粉尘：5秒/次
            case "TEMPERATURE" -> 5; // 温度：5秒/次
            case "WIND" -> 10;       // 风速：10秒/次
            default -> 5;
        };
    }

    /**
     * 根据传感器类型获取基准值
     *
     * @param type 传感器类型
     * @return 基准值
     */
    private double getBaseValueByType(String type) {
        return switch (type) {
            case "GAS" -> 0.5;
            case "DUST" -> 100;
            case "CO" -> 15;
            case "TEMPERATURE" -> 25;
            case "WIND" -> 2.5;
            default -> 0;
        };
    }

    /**
     * 根据传感器类型获取方差
     *
     * @param type 传感器类型
     * @return 方差
     */
    private double getVarianceByType(String type) {
        return switch (type) {
            case "GAS" -> 0.2;
            case "DUST" -> 50;
            case "CO" -> 8;
            case "TEMPERATURE" -> 2;
            case "WIND" -> 1;
            default -> 0;
        };
    }

    /**
     * 根据传感器类型获取单位
     *
     * @param type 传感器类型
     * @return 单位
     */
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

    /**
     * 获取模拟器运行状态
     *
     * @return true-运行中，false-已停止
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取所有传感器任务状态
     *
     * @return 传感器任务状态Map
     */
    public Map<String, Object> getSensorTaskStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        for (Map.Entry<String, SensorSimulatorConfig> entry : sensorConfigMap.entrySet()) {
            Map<String, Object> sensorStatus = new ConcurrentHashMap<>();
            SensorSimulatorConfig config = entry.getValue();
            sensorStatus.put("name", config.getName());
            sensorStatus.put("type", config.getType());
            sensorStatus.put("samplingInterval", config.getSamplingInterval());
            sensorStatus.put("unit", config.getUnit());
            sensorStatus.put("running", sensorTasks.containsKey(entry.getKey()) &&
                    !sensorTasks.get(entry.getKey()).isCancelled());
            status.put(entry.getKey(), sensorStatus);
        }
        return status;
    }

    /**
     * 传感器模拟器配置（内部类）
     * 包含从数据库和YAML合并后的完整配置
     */
    @Data
    private static class SensorSimulatorConfig {
        /**
         * 传感器ID
         */
        private String id;

        /**
         * 传感器名称
         */
        private String name;

        /**
         * 传感器类型
         */
        private String type;

        /**
         * 安装位置
         */
        private String location;

        /**
         * 采样间隔（秒）
         */
        private Integer samplingInterval;

        /**
         * 基准值
         */
        private double baseValue;

        /**
         * 方差（波动范围）
         */
        private double variance;

        /**
         * 单位
         */
        private String unit;
    }

    /**
     * YAML配置的传感器配置类
     */
    @Data
    public static class SimulatorSensorConfig {
        /**
         * 传感器ID
         */
        private String id;

        /**
         * 传感器类型
         */
        private String type;

        /**
         * 基准值
         */
        private double baseValue;

        /**
         * 方差
         */
        private double variance;
    }
}
