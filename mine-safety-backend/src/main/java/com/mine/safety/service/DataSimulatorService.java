package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.dto.SensorDataDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSimulatorService {

    private final KafkaProducerService kafkaProducerService;
    private final MqttMessageListener mqttMessageListener;

    @Value("${app.simulator.enabled:false}")
    private boolean simulatorEnabled;

    @Value("${app.simulator.interval-seconds:1}")
    private int intervalSeconds;

    @Value("${app.simulator.sensors:}")
    private List<SimulatorSensorConfig> sensorConfigs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Random random = new Random();

    @EventListener(ApplicationReadyEvent.class)
    public void startSimulator() {
        if (simulatorEnabled) {
            log.info("传感器数据模拟器已启动，间隔: {}秒", intervalSeconds);
            running.set(true);
        }
    }

    @Scheduled(fixedDelayString = "${app.simulator.interval-seconds:1}000")
    public void generateData() {
        if (!running.get() || sensorConfigs == null || sensorConfigs.isEmpty()) {
            return;
        }

        for (SimulatorSensorConfig config : sensorConfigs) {
            try {
                SensorDataDTO data = generateSensorData(config);
                if (shouldSendToMqtt(config)) {
                    sendToMqtt(data);
                } else {
                    sendToKafka(data);
                }
                log.debug("生成模拟数据 - 传感器: {}, 类型: {}, 值: {}",
                        config.getId(), config.getType(), data.getValue());
            } catch (Exception e) {
                log.error("生成模拟数据失败 - 传感器: {}, 错误: {}", config.getId(), e.getMessage());
            }
        }
    }

    private SensorDataDTO generateSensorData(SimulatorSensorConfig config) {
        SensorDataDTO dto = new SensorDataDTO();
        dto.setSensorId(config.getId());
        dto.setSensorType(config.getType());
        dto.setTimestamp(LocalDateTime.now());
        dto.setQuality(1);

        double baseValue = config.getBaseValue();
        double variance = config.getVariance();
        double value = baseValue + (random.nextGaussian() * variance);

        if (random.nextDouble() < 0.02) {
            value = baseValue * (1.5 + random.nextDouble() * 2);
            dto.setQuality(0);
        }

        value = Math.max(0, value);

        dto.setValue(BigDecimal.valueOf(value));
        dto.setUnit(getUnitByType(config.getType()));

        return dto;
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

    private boolean shouldSendToMqtt(SimulatorSensorConfig config) {
        return random.nextDouble() < 0.8;
    }

    private void sendToMqtt(SensorDataDTO data) {
        String topic = "mine/sensor/data/" + data.getSensorId();
        String payload = JSON.toJSONString(data);
        mqttMessageListener.publish(topic, payload, 1);
    }

    private void sendToKafka(SensorDataDTO data) {
        kafkaProducerService.sendRawSensorData(data);
    }

    public void start() {
        running.set(true);
        log.info("传感器数据模拟器已启动");
    }

    public void stop() {
        running.set(false);
        log.info("传感器数据模拟器已停止");
    }

    public boolean isRunning() {
        return running.get();
    }

    @Data
    public static class SimulatorSensorConfig {
        private String id;
        private String type;
        private double baseValue;
        private double variance;
    }
}
