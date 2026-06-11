package com.mine.safety.controller;

import com.mine.safety.dto.ApiResponse;
import com.mine.safety.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<Map<String, Object>> getOverview() {
        return ApiResponse.success(dashboardService.getOverview());
    }

    @GetMapping("/sensor-realtime")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<List<Map<String, Object>>> getSensorRealtime() {
        return ApiResponse.success(dashboardService.getSensorRealtime());
    }

    @GetMapping("/alert-trend")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<Map<String, Object>> getAlertTrend() {
        return ApiResponse.success(dashboardService.getAlertTrend());
    }

    @GetMapping("/alert-type-distribution")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<List<Map<String, Object>>> getAlertTypeDistribution() {
        return ApiResponse.success(dashboardService.getAlertTypeDistribution());
    }

    @GetMapping("/device-status")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<Map<String, Object>> getDeviceStatus() {
        return ApiResponse.success(dashboardService.getDeviceStatus());
    }

    @GetMapping("/personnel-distribution")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<List<Map<String, Object>>> getPersonnelDistribution() {
        return ApiResponse.success(dashboardService.getPersonnelDistribution());
    }

    @GetMapping("/heatmap")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<List<Map<String, Object>>> getHeatmap() {
        return ApiResponse.success(dashboardService.getHeatmap());
    }

    @GetMapping("/tunnel/{tunnel}/sensor-history")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<List<Map<String, Object>>> getTunnelSensorHistory(@PathVariable String tunnel) {
        return ApiResponse.success(dashboardService.getTunnelSensorHistory(tunnel));
    }

    @GetMapping("/tunnel/{tunnel}/alert-records")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<List<Map<String, Object>>> getTunnelAlertRecords(@PathVariable String tunnel) {
        return ApiResponse.success(dashboardService.getTunnelAlertRecords(tunnel));
    }
}
