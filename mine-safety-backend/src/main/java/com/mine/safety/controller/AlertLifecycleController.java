package com.mine.safety.controller;

import com.mine.safety.domain.Alert;
import com.mine.safety.domain.AlertDisposalRecord;
import com.mine.safety.domain.AlertEscalationLog;
import com.mine.safety.dto.ApiResponse;
import com.mine.safety.service.AlertLifecycleService;
import com.mine.safety.service.AlertSearchService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alert-lifecycle")
public class AlertLifecycleController {

    private final AlertLifecycleService alertLifecycleService;

    @Autowired(required = false)
    private AlertSearchService alertSearchService;

    public AlertLifecycleController(AlertLifecycleService alertLifecycleService) {
        this.alertLifecycleService = alertLifecycleService;
    }

    @PostMapping("/trigger")
    @PreAuthorize("hasAuthority('alert:edit')")
    public ApiResponse<Alert> triggerAlert(@RequestBody TriggerAlertRequest request) {
        Alert alert = alertLifecycleService.triggerAlert(
                request.getSensorId(),
                request.getSensorName(),
                request.getSensorType(),
                request.getLocation(),
                request.getTunnel(),
                request.getAlertValue(),
                request.getThresholdValue(),
                request.getThresholdType(),
                request.getLevel(),
                request.getRuleId(),
                request.getRuleName(),
                request.getDescription()
        );
        return ApiResponse.success(alert);
    }

    @PostMapping("/{alertNo}/confirm")
    @PreAuthorize("hasAuthority('alert:acknowledge')")
    public ApiResponse<Alert> confirmAlert(@PathVariable String alertNo,
                                            @RequestBody ConfirmRequest request) {
        Alert alert = alertLifecycleService.confirmAlert(alertNo, request.getConfirmedBy());
        return ApiResponse.success(alert);
    }

    @PostMapping("/{alertNo}/processing")
    @PreAuthorize("hasAuthority('alert:disposal')")
    public ApiResponse<Alert> startProcessing(@PathVariable String alertNo,
                                               @RequestBody ProcessingRequest request) {
        Alert alert = alertLifecycleService.startProcessing(alertNo, request.getProcessingBy());
        return ApiResponse.success(alert);
    }

    @PostMapping("/{alertNo}/recover")
    @PreAuthorize("hasAuthority('alert:disposal')")
    public ApiResponse<Alert> recoverAlert(@PathVariable String alertNo,
                                            @RequestBody RecoverRequest request) {
        Alert alert = alertLifecycleService.recoverAlert(
                alertNo, request.getRecoveryValue(), request.getRecoveryTime());
        return ApiResponse.success(alert);
    }

    @PostMapping("/{alertNo}/close")
    @PreAuthorize("hasAuthority('alert:disposal')")
    public ApiResponse<Alert> closeAlert(@PathVariable String alertNo,
                                          @RequestBody CloseRequest request) {
        Alert alert = alertLifecycleService.closeAlert(
                alertNo, request.getClosedBy(), request.getClosingMeasures(),
                request.getImageUrls(), request.getRemark());
        return ApiResponse.success(alert);
    }

    @PostMapping("/{alertNo}/disposal")
    @PreAuthorize("hasAuthority('alert:disposal')")
    public ApiResponse<AlertDisposalRecord> addDisposalRecord(@PathVariable String alertNo,
                                                               @RequestBody DisposalRequest request) {
        AlertDisposalRecord record = alertLifecycleService.addDisposalRecord(
                alertNo, request.getDisposalMeasures(), request.getImageUrls(),
                request.getOperator(), request.getOperatorRole(), request.getRemark());
        return ApiResponse.success(record);
    }

    @GetMapping("/{alertNo}/disposal-records")
    @PreAuthorize("hasAuthority('alert:view')")
    public ApiResponse<List<AlertDisposalRecord>> getDisposalRecords(@PathVariable String alertNo) {
        return ApiResponse.success(alertLifecycleService.getDisposalRecords(alertNo));
    }

    @GetMapping("/{alertNo}/escalation-logs")
    @PreAuthorize("hasAuthority('alert:view')")
    public ApiResponse<List<AlertEscalationLog>> getEscalationLogs(@PathVariable String alertNo) {
        return ApiResponse.success(alertLifecycleService.getEscalationLogs(alertNo));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('alert:view')")
    public ApiResponse<IPage<Alert>> searchAlerts(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String sensorId,
            @RequestParam(required = false) String tunnel,
            @RequestParam(required = false) String sensorType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<Alert> result = alertLifecycleService.searchAlerts(
                status, level, sensorId, tunnel, sensorType, startTime, endTime, page, size);
        return ApiResponse.success(result);
    }

    @GetMapping("/realtime-unconfirmed")
    @PreAuthorize("hasAuthority('alert:view')")
    public ApiResponse<List<Alert>> getRealtimeUnconfirmedAlerts() {
        return ApiResponse.success(alertLifecycleService.getRealtimeUnconfirmedAlerts());
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('alert:view')")
    public ApiResponse<Map<String, Object>> getAlertStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ApiResponse.success(alertLifecycleService.getAlertStatistics(startTime, endTime));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('report:export')")
    public ApiResponse<List<Alert>> exportAlerts(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) String tunnel,
            @RequestParam(required = false) String sensorType) {
        return ApiResponse.success(alertLifecycleService.exportAlerts(startTime, endTime, tunnel, sensorType));
    }

    @GetMapping("/es/search")
    @PreAuthorize("hasAuthority('alert:view')")
    public ApiResponse<IPage<Map<String, Object>>> esSearch(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String tunnel,
            @RequestParam(required = false) String sensorType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (alertSearchService == null) {
            return ApiResponse.error("Elasticsearch未启用，请使用数据库查询接口");
        }
        return ApiResponse.success(alertSearchService.searchAlerts(
                keyword, level, tunnel, sensorType, status, startTime, endTime, page, size));
    }

    @PostMapping("/es/reindex")
    @PreAuthorize("hasAuthority('system:config')")
    public ApiResponse<String> reindexAll() {
        if (alertSearchService == null) {
            return ApiResponse.error("Elasticsearch未启用");
        }
        alertSearchService.reindexAll();
        return ApiResponse.success("重建索引任务已启动");
    }

    @Data
    public static class TriggerAlertRequest {
        private String sensorId;
        private String sensorName;
        private String sensorType;
        private String location;
        private String tunnel;
        private BigDecimal alertValue;
        private BigDecimal thresholdValue;
        private String thresholdType;
        private String level;
        private Long ruleId;
        private String ruleName;
        private String description;
    }

    @Data
    public static class ConfirmRequest {
        private String confirmedBy;
    }

    @Data
    public static class ProcessingRequest {
        private String processingBy;
    }

    @Data
    public static class RecoverRequest {
        private BigDecimal recoveryValue;
        private LocalDateTime recoveryTime;
    }

    @Data
    public static class CloseRequest {
        private String closedBy;
        private String closingMeasures;
        private String imageUrls;
        private String remark;
    }

    @Data
    public static class DisposalRequest {
        private String disposalMeasures;
        private String imageUrls;
        private String operator;
        private String operatorRole;
        private String remark;
    }
}
