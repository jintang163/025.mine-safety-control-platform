package com.mine.safety.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mine.safety.dto.*;
import com.mine.safety.service.RealtimeMonitorService;
import com.mine.safety.service.ThresholdService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/thresholds")
@RequiredArgsConstructor
public class ThresholdController {

    private final ThresholdService thresholdService;
    private final RealtimeMonitorService realtimeMonitorService;

    @GetMapping
    @PreAuthorize("hasAuthority('threshold:view')")
    public ApiResponse<List<ThresholdDTO>> getAllThresholds(
            @RequestParam(required = false) String sensorType) {
        List<ThresholdDTO> thresholds;
        if (sensorType != null) {
            thresholds = thresholdService.getThresholdsByType(sensorType);
        } else {
            thresholds = thresholdService.getAllThresholds();
        }
        return ApiResponse.success(thresholds);
    }

    @GetMapping("/{sensorId}")
    @PreAuthorize("hasAuthority('threshold:view')")
    public ApiResponse<ThresholdDTO> getThresholdBySensorId(@PathVariable String sensorId) {
        return ApiResponse.success(thresholdService.getThresholdBySensorId(sensorId));
    }

    @GetMapping("/zone/{zoneCode}")
    @PreAuthorize("hasAuthority('threshold:view')")
    public ApiResponse<List<ThresholdDTO>> getThresholdsByZone(@PathVariable String zoneCode) {
        return ApiResponse.success(thresholdService.getThresholdsByZone(zoneCode));
    }

    @GetMapping("/mine/summary")
    @PreAuthorize("hasAuthority('threshold:view')")
    public ApiResponse<Map<String, Object>> getMineLevelSummary() {
        return ApiResponse.success(thresholdService.getMineLevelThresholdSummary());
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAuthority('threshold:apply')")
    public ApiResponse<ThresholdApprovalDTO> applyThresholdChange(
            @Valid @RequestBody ThresholdApplyDTO dto) {
        return ApiResponse.success(thresholdService.applyThresholdChange(dto));
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAuthority('threshold:approve')")
    public ApiResponse<ThresholdApprovalDTO> approveThreshold(
            @Valid @RequestBody ApprovalActionDTO dto) {
        return ApiResponse.success(thresholdService.approveThreshold(dto));
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('threshold:view')")
    public ApiResponse<IPage<ThresholdApprovalDTO>> getApprovalList(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(thresholdService.getApprovalList(status, page, size));
    }

    @GetMapping("/approvals/{approvalNo}")
    @PreAuthorize("hasAuthority('threshold:view')")
    public ApiResponse<ThresholdApprovalDTO> getApprovalDetail(@PathVariable String approvalNo) {
        return ApiResponse.success(thresholdService.getApprovalDetail(approvalNo));
    }

    @GetMapping("/approvals/statistics")
    @PreAuthorize("hasAuthority('threshold:view')")
    public ApiResponse<Map<String, Object>> getApprovalStatistics() {
        return ApiResponse.success(thresholdService.getApprovalStatistics());
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('threshold:view')")
    public ApiResponse<IPage<ThresholdAuditDTO>> getAuditList(
            @RequestParam(required = false) String sensorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(thresholdService.getAuditList(sensorId, startTime, endTime, page, size));
    }

    @GetMapping("/monitor/channel/{sensorId}")
    @PreAuthorize("hasAuthority('threshold:view')")
    public ApiResponse<RealtimeMonitorDTO> getChannelMonitor(@PathVariable String sensorId) {
        return ApiResponse.success(realtimeMonitorService.getChannelMonitor(sensorId));
    }

    @GetMapping("/monitor/zone/{zoneCode}")
    @PreAuthorize("hasAuthority('threshold:view')")
    public ApiResponse<RealtimeMonitorDTO.ZoneMonitorDTO> getZoneMonitor(@PathVariable String zoneCode) {
        return ApiResponse.success(realtimeMonitorService.getZoneMonitor(zoneCode));
    }

    @GetMapping("/monitor/mine")
    @PreAuthorize("hasAuthority('threshold:view')")
    public ApiResponse<RealtimeMonitorDTO.MineMonitorDTO> getMineMonitor() {
        return ApiResponse.success(realtimeMonitorService.getMineMonitor());
    }
}
