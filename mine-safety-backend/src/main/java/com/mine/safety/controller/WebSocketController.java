package com.mine.safety.controller;

import com.mine.safety.config.WebSocketEventListener;
import com.mine.safety.dto.RealtimeMonitorDTO;
import com.mine.safety.service.RealtimeMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final RealtimeMonitorService realtimeMonitorService;
    private final WebSocketEventListener webSocketEventListener;

    @SubscribeMapping("/realtime-monitor")
    public RealtimeMonitorDTO.MineMonitorDTO subscribeRealtimeMonitor() {
        log.debug("客户端订阅实时监测数据");
        return realtimeMonitorService.getMineMonitor();
    }

    @SubscribeMapping("/connection-status")
    public Map<String, Object> getConnectionStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connectedClients", webSocketEventListener.getConnectedClientCount());
        status.put("serverTime", java.time.LocalDateTime.now().toString());
        status.put("status", "connected");
        return status;
    }

    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public Map<String, Object> handlePing(Map<String, Object> message) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        response.put("received", message);
        return response;
    }

    @MessageMapping("/monitor/request")
    @SendTo("/topic/realtime-monitor")
    public RealtimeMonitorDTO.MineMonitorDTO requestMonitorData(String request) {
        log.debug("收到监测数据请求: {}", request);
        return realtimeMonitorService.getMineMonitor();
    }
}
