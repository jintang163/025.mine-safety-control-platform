package com.mine.safety.controller;

import com.mine.safety.dto.ApiResponse;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 传感器数据查询控制器
 * 提供传感器数据的REST API查询接口，包括实时数据、历史数据、
 * 聚合数据和统计数据。
 *
 * 接口列表：
 *   - GET /sensor-data/latest                 获取所有传感器最新数据
 *   - GET /sensor-data/{id}/latest            获取单个传感器最新数据
 *   - GET /sensor-data/{id}/history           查询历史数据（原始数据）
 *   - GET /sensor-data/{id}/aggregated        查询历史数据（带聚合）
 *   - GET /sensor-data/{id}/statistics        查询统计数据（均值/最大/最小）
 *   - GET /sensor-data/{id}/export            导出数据（预留）
 *
 * @author mine-safety
 * @since 1.0.0
 */
@RestController
@RequestMapping("/sensor-data")
@RequiredArgsConstructor
public class SensorDataController {

    /** 传感器服务 */
    private final SensorService sensorService;

    /**
     * 获取所有传感器的最新数据
     * 用于首页仪表盘展示，返回所有传感器的实时状态。
     *
     * @return 所有传感器的最新数据列表
     */
    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('sensor:view')")
    public ApiResponse<List<SensorDataDTO>> getAllLatestData() {
        return ApiResponse.success(sensorService.getAllLatestData());
    }

    /**
     * 获取单个传感器的最新数据
     *
     * @param sensorId 传感器ID
     * @return 最新数据（可能为null）
     */
    @GetMapping("/{sensorId}/latest")
    @PreAuthorize("hasAuthority('sensor:view')")
    public ApiResponse<SensorDataDTO> getLatestData(@PathVariable String sensorId) {
        return ApiResponse.success(sensorService.getLatestSensorData(sensorId));
    }

    /**
     * 查询传感器历史数据（原始数据）
     * 查询指定时间范围内的原始数据点，最多返回1000条。
     *
     * @param sensorId  传感器ID
     * @param startTime 开始时间（ISO格式，如2024-01-01T00:00:00）
     * @param endTime   结束时间
     * @return 历史数据列表（按时间倒序）
     */
    @GetMapping("/{sensorId}/history")
    @PreAuthorize("hasAuthority('sensor:view')")
    public ApiResponse<List<SensorDataDTO>> getHistory(
            @PathVariable String sensorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.success(sensorService.getSensorDataHistory(sensorId, startTime, endTime));
    }

    /**
     * 查询传感器历史数据（带聚合）
     * 按指定时间窗口和聚合函数对数据进行降采样，
     * 适用于大范围时间查询（如查询一天的数据）。
     *
     * 支持的聚合函数：
     *   - mean: 平均值（默认）
     *   - max: 最大值
     *   - min: 最小值
     *   - count: 计数
     *   - sum: 求和
     *
     * 时间窗口示例：1m（1分钟）、5m（5分钟）、1h（1小时）、1d（1天）
     *
     * @param sensorId    传感器ID
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @param aggregation 聚合函数（默认mean）
     * @param window      时间窗口（默认1m）
     * @return 聚合后的数据列表
     */
    @GetMapping("/{sensorId}/aggregated")
    @PreAuthorize("hasAuthority('sensor:view')")
    public ApiResponse<List<SensorDataDTO>> getAggregatedData(
            @PathVariable String sensorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "mean") String aggregation,
            @RequestParam(defaultValue = "1m") String window) {
        return ApiResponse.success(sensorService.getSensorDataAggregated(
                sensorId, startTime, endTime, aggregation, window));
    }

    /**
     * 查询传感器统计数据
     * 一次返回指定时间范围内的均值、最大值、最小值。
     *
     * @param sensorId  传感器ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计结果Map（key: mean/max/min）
     */
    @GetMapping("/{sensorId}/statistics")
    @PreAuthorize("hasAuthority('sensor:view')")
    public ApiResponse<Map<String, Object>> getStatistics(
            @PathVariable String sensorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.success(sensorService.getSensorStatistics(sensorId, startTime, endTime));
    }

    /**
     * 导出传感器数据（预留接口）
     * 支持导出CSV、Excel等格式。
     *
     * @param sensorId  传感器ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param format    导出格式（默认csv）
     * @return 导出结果
     */
    @GetMapping("/{sensorId}/export")
    @PreAuthorize("hasAuthority('report:export')")
    public ApiResponse<String> exportData(
            @PathVariable String sensorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "csv") String format) {
        return ApiResponse.success("导出功能待实现");
    }
}
