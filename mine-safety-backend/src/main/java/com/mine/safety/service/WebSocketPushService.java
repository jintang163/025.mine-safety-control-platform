package com.mine.safety.service;

import com.mine.safety.dto.AlertDTO;
import com.mine.safety.dto.RealtimeMonitorDTO;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.dto.ThresholdDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final SimpMessagingTemplate messagingTemplate;

    private static final String TOPIC_ALERTS = "/topic/alerts";
    private static final String TOPIC_SENSOR_DATA = "/topic/sensor-data";
    private static final String TOPIC_THRESHOLD_UPDATE = "/topic/threshold-updates";
    private static final String TOPIC_REALTIME_MONITOR = "/topic/realtime-monitor";
    private static final String TOPIC_SENSOR_PREFIX = "/topic/sensor/";

    public void pushAlert(AlertDTO alert) {
        try {
            messagingTemplate.convertAndSend(TOPIC_ALERTS, alert);
            messagingTemplate.convertAndSend(TOPIC_SENSOR_PREFIX + alert.getSensorId() + "/alerts", alert);
            log.debug("WebSocket推送报警 - 传感器: {}, 级别: {}", alert.getSensorId(), alert.getLevel());
        } catch (Exception e) {
            log.warn("WebSocket推送报警失败: {}", e.getMessage());
        }
    }

    public void pushSensorData(SensorDataDTO data) {
        try {
            messagingTemplate.convertAndSend(TOPIC_SENSOR_DATA, data);
            messagingTemplate.convertAndSend(TOPIC_SENSOR_PREFIX + data.getSensorId(), data);
            log.debug("WebSocket推送传感器数据 - 传感器: {}, 值: {}", data.getSensorId(), data.getValue());
        } catch (Exception e) {
            log.warn("WebSocket推送传感器数据失败: {}", e.getMessage());
        }
    }

    public void pushThresholdUpdate(ThresholdDTO threshold) {
        try {
            messagingTemplate.convertAndSend(TOPIC_THRESHOLD_UPDATE, threshold);
            messagingTemplate.convertAndSend(TOPIC_SENSOR_PREFIX + threshold.getSensorId() + "/threshold", threshold);
            log.info("WebSocket推送阈值更新 - 传感器: {}", threshold.getSensorId());
        } catch (Exception e) {
            log.warn("WebSocket推送阈值更新失败: {}", e.getMessage());
        }
    }

    public void pushRealtimeMonitor(RealtimeMonitorDTO.MineMonitorDTO monitorData) {
        try {
            messagingTemplate.convertAndSend(TOPIC_REALTIME_MONITOR, monitorData);
            log.debug("WebSocket推送实时监测数据 - 矿井: {}", monitorData.getMineName());
        } catch (Exception e) {
            log.warn("WebSocket推送实时监测数据失败: {}", e.getMessage());
        }
    }

    public void pushZoneMonitor(RealtimeMonitorDTO.ZoneMonitorDTO zoneData) {
        try {
            messagingTemplate.convertAndSend("/topic/zone/" + zoneData.getZoneCode() + "/monitor", zoneData);
            log.debug("WebSocket推送区域监测数据 - 区域: {}", zoneData.getZoneName());
        } catch (Exception e) {
            log.warn("WebSocket推送区域监测数据失败: {}", e.getMessage());
        }
    }

    public void pushCustomMessage(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("WebSocket推送自定义消息 - 目标: {}", destination);
        } catch (Exception e) {
            log.warn("WebSocket推送自定义消息失败: {}", e.getMessage());
        }
    }
}
