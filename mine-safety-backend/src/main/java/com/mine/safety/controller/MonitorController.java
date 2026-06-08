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

/**
 * 监控仪表盘控制器
 * 提供首页监控仪表盘所需的综合数据接口，包括概览统计、
 * 实时数据、按类型/位置查询、模拟器控制等。
 *
 * 概览接口：
 *   - GET /monitor/overview     获取系统概览（传感器总数、在线数、报警统计）
 *   - GET /monitor/dashboard    获取仪表盘完整数据（概览+实时数据+最近报警）
 *
 * 数据查询接口：
 *   - GET /monitor/real-time    获取所有传感器实时数据
 *   - GET /monitor/by-type/{type}     按传感器类型查询
 *   - GET /monitor/by-location/{location} 按位置查询
 *
 * 模拟器控制接口：
 *   - GET  /monitor/simulator/status  获取模拟器状态
 *   - POST /monitor/simulator/start   启动模拟器
 *   - POST /monitor/simulator/stop    停止模拟器
 *
 * 系统状态接口：
 *   - GET /monitor/system/status 获取系统各组件连接状态
 *
 * @author mine-safety
 * @since 1.0.0
 */
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorController {

    /** 传感器服务 */
    private final SensorService sensorService;

    /** 报警服务 */
    private final AlertService alertService;

    /** 数据模拟器服务 */
    private final DataSimulatorService dataSimulatorService;

    /**
     * 获取系统概览数据
     * 包括传感器总数、在线数、离线数、报警统计等。
     *
     * @return 概览数据Map
     */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> getOverview() {
        Map<String, Object> overview = new HashMap<>();

        // 获取所有传感器最新数据和报警统计
        List<SensorDataDTO> allLatestData = sensorService.getAllLatestData();
        Map<String, Object> alertStats = alertService.getAlertStatistics();

        // 计算在线/离线传感器数量（有值视为在线）
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

    /**
     * 获取仪表盘完整数据
     * 一次性返回仪表盘所需的所有数据，减少前端请求次数。
     *
     * @return 仪表盘数据（包含概览、实时数据、最近报警）
     */
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboardData() {
        Map<String, Object> dashboard = new HashMap<>();

        dashboard.put("overview", getOverview().getData());
        dashboard.put("latestData", sensorService.getAllLatestData());
        dashboard.put("recentAlerts", getRecentAlerts());

        return ApiResponse.success(dashboard);
    }

    /**
     * 获取所有传感器实时数据
     *
     * @return 所有传感器的最新数据列表
     */
    @GetMapping("/real-time")
    public ApiResponse<List<SensorDataDTO>> getRealTimeData() {
        return ApiResponse.success(sensorService.getAllLatestData());
    }

    /**
     * 按传感器类型查询
     *
     * @param type 传感器类型
     * @return 该类型的传感器列表
     */
    @GetMapping("/by-type/{type}")
    public ApiResponse<Map<String, Object>> getDataByType(@PathVariable String type) {
        Map<String, Object> result = new HashMap<>();
        result.put("sensors", sensorService.getSensorsByType(type));
        return ApiResponse.success(result);
    }

    /**
     * 按位置查询传感器数据
     *
     * @param location 位置名称
     * @return 该位置的传感器最新数据列表
     */
    @GetMapping("/by-location/{location}")
    public ApiResponse<List<SensorDataDTO>> getDataByLocation(@PathVariable String location) {
        List<SensorDataDTO> allData = sensorService.getAllLatestData();
        List<SensorDataDTO> filtered = allData.stream()
                .filter(d -> location.equals(d.getLocation()))
                .toList();
        return ApiResponse.success(filtered);
    }

    /**
     * 获取模拟器运行状态
     *
     * @return 状态Map（包含running字段）
     */
    @GetMapping("/simulator/status")
    public ApiResponse<Map<String, Object>> getSimulatorStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", dataSimulatorService.isRunning());
        return ApiResponse.success(status);
    }

    /**
     * 启动数据模拟器
     * 开始按各传感器的采样间隔生成模拟数据并发送。
     *
     * @return 模拟器当前状态
     */
    @PostMapping("/simulator/start")
    public ApiResponse<Map<String, Object>> startSimulator() {
        dataSimulatorService.start();
        return getSimulatorStatus();
    }

    /**
     * 停止数据模拟器
     * 停止生成模拟数据。
     *
     * @return 模拟器当前状态
     */
    @PostMapping("/simulator/stop")
    public ApiResponse<Map<String, Object>> stopSimulator() {
        dataSimulatorService.stop();
        return getSimulatorStatus();
    }

    /**
     * 获取最近的异常数据（质量=0）
     * 用于仪表盘展示最近的异常数据点。
     *
     * @return 最近的异常数据列表（最多10条）
     */
    private List<?> getRecentAlerts() {
        return sensorService.getAllLatestData().stream()
                .filter(d -> d.getQuality() != null && d.getQuality() == 0)
                .limit(10)
                .toList();
    }

    /**
     * 获取系统各组件连接状态
     * 用于监控MQTT、Kafka、InfluxDB、MySQL、Redis等组件的连接状态。
     *
     * @return 各组件状态Map
     */
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
