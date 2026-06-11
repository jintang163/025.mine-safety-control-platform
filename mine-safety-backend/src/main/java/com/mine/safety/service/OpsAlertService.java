package com.mine.safety.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpsAlertService {

    private final SystemMetricsService systemMetricsService;
    private final DingTalkService dingTalkService;
    private final SmsService smsService;

    @Value("${app.ops-alert.enabled:true}")
    private boolean opsAlertEnabled;

    @Value("${app.ops-alert.data-delay-threshold-ms:5000}")
    private long dataDelayThresholdMs;

    @Value("${app.ops-alert.mqtt-reconnect-alert:true}")
    private boolean mqttReconnectAlert;

    @Value("${app.ops-alert.notify-channels:DINGTALK,SMS}")
    private String notifyChannels;

    private final AtomicBoolean mqttDisconnectedAlertSent = new AtomicBoolean(false);
    private final AtomicBoolean dataDelayAlertSent = new AtomicBoolean(false);
    private final AtomicLong lastAlertTime = new AtomicLong(0);

    private static final long ALERT_COOLDOWN_MS = 5 * 60 * 1000;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Scheduled(fixedDelayString = "${app.ops-alert.check-interval-seconds:30}000")
    public void checkSystemHealth() {
        if (!opsAlertEnabled) {
            return;
        }

        checkMqttConnection();
        checkDataDelay();
    }

    private void checkMqttConnection() {
        if (!mqttReconnectAlert) {
            return;
        }

        boolean connected = systemMetricsService.isMqttConnected();
        if (!connected && !mqttDisconnectedAlertSent.get()) {
            String alertContent = buildMqttDisconnectAlert();
            sendAlert("【严重】MQTT连接断开告警", alertContent);
            mqttDisconnectedAlertSent.set(true);
            systemMetricsService.incrementOpsAlertTriggered();
            log.error("MQTT connection disconnected, alert sent");
        } else if (connected && mqttDisconnectedAlertSent.get()) {
            String recoverContent = buildMqttRecoverAlert();
            sendAlert("【恢复】MQTT连接恢复通知", recoverContent);
            mqttDisconnectedAlertSent.set(false);
            log.info("MQTT connection recovered, recover notification sent");
        }
    }

    private void checkDataDelay() {
        long currentDelay = systemMetricsService.getSensorDataDelayMs();
        if (currentDelay > dataDelayThresholdMs && !dataDelayAlertSent.get()) {
            String alertContent = buildDataDelayAlert(currentDelay);
            sendAlert("【警告】数据处理延迟过高告警", alertContent);
            dataDelayAlertSent.set(true);
            systemMetricsService.incrementOpsAlertTriggered();
            log.warn("Data delay exceeds threshold: {}ms > {}ms", currentDelay, dataDelayThresholdMs);
        } else if (currentDelay <= dataDelayThresholdMs / 2 && dataDelayAlertSent.get()) {
            String recoverContent = buildDataDelayRecoverAlert(currentDelay);
            sendAlert("【恢复】数据处理延迟恢复正常", recoverContent);
            dataDelayAlertSent.set(false);
            log.info("Data delay recovered to normal: {}ms", currentDelay);
        }
    }

    private String buildMqttDisconnectAlert() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🚨 MQTT连接断开告警\n\n");
        sb.append("**告警时间：** ").append(LocalDateTime.now().format(FORMATTER)).append("\n\n");
        sb.append("**告警级别：** 严重\n\n");
        sb.append("**告警内容：** MQTT Broker连接已断开\n\n");
        sb.append("**影响范围：** 传感器数据无法正常接收\n\n");
        sb.append("**请立即检查：**\n");
        sb.append("- MQTT Broker服务状态\n");
        sb.append("- 网络连接状态\n");
        sb.append("- Broker配置是否正确\n");
        return sb.toString();
    }

    private String buildMqttRecoverAlert() {
        StringBuilder sb = new StringBuilder();
        sb.append("## ✅ MQTT连接恢复通知\n\n");
        sb.append("**恢复时间：** ").append(LocalDateTime.now().format(FORMATTER)).append("\n\n");
        sb.append("**通知内容：** MQTT Broker连接已恢复正常\n\n");
        sb.append("**状态：** 已恢复\n");
        return sb.toString();
    }

    private String buildDataDelayAlert(long delayMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ⚠️ 数据处理延迟过高告警\n\n");
        sb.append("**告警时间：** ").append(LocalDateTime.now().format(FORMATTER)).append("\n\n");
        sb.append("**告警级别：** 警告\n\n");
        sb.append("**当前延迟：** ").append(delayMs).append("ms\n\n");
        sb.append("**阈值：** ").append(dataDelayThresholdMs).append("ms\n\n");
        sb.append("**影响范围：** 报警响应可能不及时\n\n");
        sb.append("**建议检查：**\n");
        sb.append("- 系统CPU和内存使用率\n");
        sb.append("- 数据库连接池状态\n");
        sb.append("- Kafka消息堆积情况\n");
        sb.append("- 规则引擎处理效率\n");
        return sb.toString();
    }

    private String buildDataDelayRecoverAlert(long delayMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ✅ 数据处理延迟恢复通知\n\n");
        sb.append("**恢复时间：** ").append(LocalDateTime.now().format(FORMATTER)).append("\n\n");
        sb.append("**当前延迟：** ").append(delayMs).append("ms\n\n");
        sb.append("**状态：** 已恢复正常\n");
        return sb.toString();
    }

    private void sendAlert(String title, String content) {
        long now = System.currentTimeMillis();
        if (now - lastAlertTime.get() < ALERT_COOLDOWN_MS) {
            log.debug("Alert in cooldown period, skip sending");
            return;
        }

        lastAlertTime.set(now);

        if (notifyChannels.contains("DINGTALK")) {
            dingTalkService.sendMarkdownMessage(title, content);
        }

        if (notifyChannels.contains("SMS")) {
            smsService.sendSmsToOps(title + ": " + content.substring(0, Math.min(content.length(), 100)));
        }
    }

    public void triggerCustomAlert(String title, String content, String level) {
        String alertContent = buildCustomAlert(title, content, level);
        sendAlert(title, alertContent);
        systemMetricsService.incrementOpsAlertTriggered();
    }

    private String buildCustomAlert(String title, String content, String level) {
        StringBuilder sb = new StringBuilder();
        String emoji = "⚠️";
        if ("严重".equals(level) || "CRITICAL".equalsIgnoreCase(level)) {
            emoji = "🚨";
        } else if ("警告".equals(level) || "WARNING".equalsIgnoreCase(level)) {
            emoji = "⚠️";
        } else if ("信息".equals(level) || "INFO".equalsIgnoreCase(level)) {
            emoji = "ℹ️";
        }

        sb.append("## ").append(emoji).append(" ").append(title).append("\n\n");
        sb.append("**告警时间：** ").append(LocalDateTime.now().format(FORMATTER)).append("\n\n");
        sb.append("**告警级别：** ").append(level).append("\n\n");
        sb.append("**告警内容：** ").append(content).append("\n");
        return sb.toString();
    }

    public boolean isMqttDisconnectedAlertSent() {
        return mqttDisconnectedAlertSent.get();
    }

    public boolean isDataDelayAlertSent() {
        return dataDelayAlertSent.get();
    }
}
