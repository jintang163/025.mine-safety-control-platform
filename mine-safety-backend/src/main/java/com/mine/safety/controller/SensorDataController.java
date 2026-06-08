package com.mine.safety.controller;

import com.mine.safety.dto.ApiResponse;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sensor-data")
@RequiredArgsConstructor
public class SensorDataController {

    private final SensorService sensorService;

    @GetMapping("/latest")
    public ApiResponse<List<SensorDataDTO>> getAllLatestData() {
        return ApiResponse.success(sensorService.getAllLatestData());
    }

    @GetMapping("/{sensorId}/latest")
    public ApiResponse<SensorDataDTO> getLatestData(@PathVariable String sensorId) {
        return ApiResponse.success(sensorService.getLatestSensorData(sensorId));
    }

    @GetMapping("/{sensorId}/history")
    public ApiResponse<List<SensorDataDTO>> getHistory(
            @PathVariable String sensorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.success(sensorService.getSensorDataHistory(sensorId, startTime, endTime));
    }

    @GetMapping("/{sensorId}/aggregated")
    public ApiResponse<List<SensorDataDTO>> getAggregatedData(
            @PathVariable String sensorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "mean") String aggregation,
            @RequestParam(defaultValue = "1m") String window) {
        return ApiResponse.success(sensorService.getSensorDataAggregated(
                sensorId, startTime, endTime, aggregation, window));
    }

    @GetMapping("/{sensorId}/statistics")
    public ApiResponse<Map<String, Object>> getStatistics(
            @PathVariable String sensorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.success(sensorService.getSensorStatistics(sensorId, startTime, endTime));
    }

    @GetMapping("/{sensorId}/export")
    public ApiResponse<String> exportData(
            @PathVariable String sensorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "csv") String format) {
        return ApiResponse.success("导出功能待实现");
    }
}
