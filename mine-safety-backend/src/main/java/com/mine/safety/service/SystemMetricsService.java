package com.mine.safety.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class SystemMetricsService {

    private final MeterRegistry meterRegistry;

    private final AtomicLong mqttConnectionStatus = new AtomicLong(1);
    private final AtomicLong sensorDataDelayMs = new AtomicLong(0);
    private final AtomicLong activeSensors = new AtomicLong(0);
    private final AtomicLong pendingAlerts = new AtomicLong(0);

    private Counter alertTriggeredCounter;
    private Counter alertAcknowledgedCounter;
    private Counter sensorDataReceivedCounter;
    private Counter mqttReconnectCounter;
    private Counter opsAlertTriggeredCounter;

    private Timer sensorDataProcessingTimer;

    public SystemMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        Gauge.builder("mine.mqtt.connection.status", mqttConnectionStatus, AtomicLong::get)
                .description("MQTT连接状态 (1=已连接, 0=已断开)")
                .register(meterRegistry);

        Gauge.builder("mine.sensor.data.delay.ms", sensorDataDelayMs, AtomicLong::get)
                .description("传感器数据接收延迟(毫秒)")
                .register(meterRegistry);

        Gauge.builder("mine.sensors.active.count", activeSensors, AtomicLong::get)
                .description("活跃传感器数量")
                .register(meterRegistry);

        Gauge.builder("mine.alerts.pending.count", pendingAlerts, AtomicLong::get)
                .description("待处理报警数量")
                .register(meterRegistry);

        alertTriggeredCounter = Counter.builder("mine.alerts.triggered.total")
                .description("触发报警总数")
                .register(meterRegistry);

        alertAcknowledgedCounter = Counter.builder("mine.alerts.acknowledged.total")
                .description("已确认报警总数")
                .register(meterRegistry);

        sensorDataReceivedCounter = Counter.builder("mine.sensor.data.received.total")
                .description("接收传感器数据总数")
                .register(meterRegistry);

        mqttReconnectCounter = Counter.builder("mine.mqtt.reconnect.total")
                .description("MQTT重连次数")
                .register(meterRegistry);

        opsAlertTriggeredCounter = Counter.builder("mine.ops.alerts.triggered.total")
                .description("运维告警触发次数")
                .register(meterRegistry);

        sensorDataProcessingTimer = Timer.builder("mine.sensor.data.processing.duration")
                .description("传感器数据处理耗时")
                .register(meterRegistry);

        log.info("System metrics initialized");
    }

    public void setMqttConnected(boolean connected) {
        mqttConnectionStatus.set(connected ? 1 : 0);
        if (!connected) {
            mqttReconnectCounter.increment();
        }
    }

    public boolean isMqttConnected() {
        return mqttConnectionStatus.get() == 1;
    }

    public void setSensorDataDelay(long delayMs) {
        sensorDataDelayMs.set(delayMs);
    }

    public long getSensorDataDelayMs() {
        return sensorDataDelayMs.get();
    }

    public void setActiveSensors(long count) {
        activeSensors.set(count);
    }

    public void setPendingAlerts(long count) {
        pendingAlerts.set(count);
    }

    public void incrementAlertTriggered() {
        alertTriggeredCounter.increment();
    }

    public void incrementAlertAcknowledged() {
        alertAcknowledgedCounter.increment();
    }

    public void incrementSensorDataReceived() {
        sensorDataReceivedCounter.increment();
    }

    public void incrementOpsAlertTriggered() {
        opsAlertTriggeredCounter.increment();
    }

    public Timer.Sample startSensorDataProcessing() {
        return Timer.start(meterRegistry);
    }

    public void stopSensorDataProcessing(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(sensorDataProcessingTimer);
        }
    }
}
