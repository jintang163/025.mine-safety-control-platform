package com.mine.safety.controller;

import com.mine.safety.dto.ApiResponse;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.service.AlertService;
import com.mine.safety.service.DataSimulatorService;
import com.mine.safety.service.SensorService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final SensorService sensorService;
    private final AlertService alertService;
    private final DataSimulatorService dataSimulatorService;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> getOverview() {
        Map<String, Object> overview = new HashMap<>();

        List<SensorDataDTO> allLatestData = sensorService.getAllLatestData();
        Map<String, Object> alertStats = alertService.getAlertStatistics();

        long onlineCount = allLatestData.stream()
                .filter(d -> d.getValue() != null)
                .count();
        long offlineCount = allLatestData.size() - onlineCount;

        overview.put("totalSensors", allLatestData.size());
        overview.put("onlineSensors", onlineCount);
        overview.put("offlineSensors", offlineCount);
        overview.put("alertStatistics", alertStats);
        overview.put("lastUpdate", LocalDateTime.now());

        return ApiResponse.success(overview);
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboardData() {
        Map<String, Object> dashboard = new HashMap<>();

        dashboard.put("overview", getOverview().getData());
        dashboard.put("latestData", sensorService.getAllLatestData());
        dashboard.put("recentAlerts", getRecentAlerts());

        return ApiResponse.success(dashboard);
    }

    @GetMapping("/real-time")
    public ApiResponse<List<SensorDataDTO>> getRealTimeData() {
        return ApiResponse.success(sensorService.getAllLatestData());
    }

    @GetMapping("/by-type/{type}")
    public ApiResponse<Map<String, Object>> getDataByType(@PathVariable String type) {
        Map<String, Object> result = new HashMap<>();
        result.put("sensors", sensorService.getSensorsByType(type));
        return ApiResponse.success(result);
    }

    @GetMapping("/by-location/{location}")
    public ApiResponse<List<SensorDataDTO>> getDataByLocation(@PathVariable String location) {
        List<SensorDataDTO> allData = sensorService.getAllLatestData();
        List<SensorDataDTO> filtered = allData.stream()
                .filter(d -> location.equals(d.getLocation()))
                .toList();
        return ApiResponse.success(filtered);
    }

    @GetMapping("/simulator/status")
    public ApiResponse<Map<String, Object>> getSimulatorStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", dataSimulatorService.isRunning());
        return ApiResponse.success(status);
    }

    @PostMapping("/simulator/start")
    public ApiResponse<Map<String, Object>> startSimulator() {
        dataSimulatorService.start();
        return getSimulatorStatus();
    }

    @PostMapping("/simulator/stop")
    public ApiResponse<Map<String, Object>> stopSimulator() {
        dataSimulatorService.stop();
        return getSimulatorStatus();
    }

    private List<?> getRecentAlerts() {
        return sensorService.getAllLatestData().stream()
                .filter(d -> d.getQuality() != null && d.getQuality() == 0)
                .limit(10)
                .toList();
    }

    @GetMapping("/system/status")
    public ApiResponse<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("mqtt", "connected");
        status.put("kafka", "connected");
        status.put("influxdb", "connected");
        status.put("mysql", "connected");
        status.put("redis", "connected");
        status.put("timestamp", LocalDateTime.now());
        return ApiResponse.success(status);
    }
}
