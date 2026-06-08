package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.dto.AlertDTO;
import com.mine.safety.dto.RealtimeMonitorDTO;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.dto.ThresholdDTO;
import com.mine.safety.netty.WebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final WebSocketServer webSocketServer;

    private static final String TOPIC_ALERTS = "alerts";
    private static final String TOPIC_SENSOR_DATA = "sensor-data";
    private static final String TOPIC_THRESHOLD_UPDATE = "threshold-updates";
    private static final String TOPIC_REALTIME_MONITOR = "realtime-monitor";
    private static final String TOPIC_SENSOR_PREFIX = "sensor/";

    public void pushAlert(AlertDTO alert) {
        try {
            webSocketServer.sendToTopic(TOPIC_ALERTS, buildPushMessage("ALERT", alert));
            webSocketServer.sendToTopic(TOPIC_SENSOR_PREFIX + alert.getSensorId() + "/alerts",
                    buildPushMessage("ALERT", alert));
            log.debug("Netty WebSocket推送报警 - 传感器: {}, 级别: {}", alert.getSensorId(), alert.getLevel());
        } catch (Exception e) {
            log.warn("Netty WebSocket推送报警失败: {}", e.getMessage());
        }
    }

    public void pushSensorData(SensorDataDTO data) {
        try {
            webSocketServer.sendToTopic(TOPIC_SENSOR_DATA, buildPushMessage("SENSOR_DATA", data));
            webSocketServer.sendToTopic(TOPIC_SENSOR_PREFIX + data.getSensorId(),
                    buildPushMessage("SENSOR_DATA", data));
            log.debug("Netty WebSocket推送传感器数据 - 传感器: {}, 值: {}", data.getSensorId(), data.getValue());
        } catch (Exception e) {
            log.warn("Netty WebSocket推送传感器数据失败: {}", e.getMessage());
        }
    }

    public void pushThresholdUpdate(ThresholdDTO threshold) {
        try {
            webSocketServer.sendToTopic(TOPIC_THRESHOLD_UPDATE, buildPushMessage("THRESHOLD_UPDATE", threshold));
            webSocketServer.sendToTopic(TOPIC_SENSOR_PREFIX + threshold.getSensorId() + "/threshold",
                    buildPushMessage("THRESHOLD_UPDATE", threshold));
            log.info("Netty WebSocket推送阈值更新 - 传感器: {}", threshold.getSensorId());
        } catch (Exception e) {
            log.warn("Netty WebSocket推送阈值更新失败: {}", e.getMessage());
        }
    }

    public void pushRealtimeMonitor(RealtimeMonitorDTO.MineMonitorDTO monitorData) {
        try {
            webSocketServer.sendToTopic(TOPIC_REALTIME_MONITOR, buildPushMessage("REALTIME_MONITOR", monitorData));
            log.debug("Netty WebSocket推送实时监测数据 - 矿井: {}", monitorData.getMineName());
        } catch (Exception e) {
            log.warn("Netty WebSocket推送实时监测数据失败: {}", e.getMessage());
        }
    }

    public void pushZoneMonitor(RealtimeMonitorDTO.ZoneMonitorDTO zoneData) {
        try {
            webSocketServer.sendToTopic("zone/" + zoneData.getZoneCode() + "/monitor",
                    buildPushMessage("ZONE_MONITOR", zoneData));
            log.debug("Netty WebSocket推送区域监测数据 - 区域: {}", zoneData.getZoneName());
        } catch (Exception e) {
            log.warn("Netty WebSocket推送区域监测数据失败: {}", e.getMessage());
        }
    }

    public void pushCustomMessage(String destination, Object payload) {
        try {
            webSocketServer.sendToTopic(destination, buildPushMessage("CUSTOM", payload));
            log.debug("Netty WebSocket推送自定义消息 - 目标: {}", destination);
        } catch (Exception e) {
            log.warn("Netty WebSocket推送自定义消息失败: {}", e.getMessage());
        }
    }

    public int getConnectedClientCount() {
        return webSocketServer.getConnectedClientCount();
    }

    private String buildPushMessage(String dataType, Object payload) {
        return JSON.toJSONString(Map.of(
                "type", "data",
                "dataType", dataType,
                "payload", payload,
                "timestamp", System.currentTimeMillis()
        ));
    }
}
