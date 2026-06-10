package com.mine.safety.controller;

import com.mine.safety.dto.*;
import com.mine.safety.service.*;
import com.mine.safety.domain.TrendAlert;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final HistoryAnalysisService historyAnalysisService;
    private final ReportService reportService;
    private final TrendAnalysisService trendAnalysisService;

    @GetMapping("/history/{sensorId}")
    public ApiResponse<HistoryStatisticsDTO> getHistoryStatistics(
            @PathVariable String sensorId,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "HOUR") String timeDimension) {
        return ApiResponse.success(historyAnalysisService.getHistoryStatistics(
                sensorId, startDate, endDate, timeDimension));
    }

    @GetMapping("/history/type/{sensorType}")
    public ApiResponse<List<HistoryStatisticsDTO>> getHistoryStatisticsByType(
            @PathVariable String sensorType,
            @RequestParam(required = false) String zoneCode,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "DAY") String timeDimension) {
        return ApiResponse.success(historyAnalysisService.getHistoryStatisticsByType(
                sensorType, zoneCode, startDate, endDate, timeDimension));
    }

    @GetMapping("/history/overview")
    public ApiResponse<Map<String, Object>> getOverviewStatistics(
            @RequestParam(required = false) String sensorType,
            @RequestParam(required = false) String zoneCode,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return ApiResponse.success(historyAnalysisService.getOverviewStatistics(
                sensorType, zoneCode, startDate, endDate));
    }

    @GetMapping("/templates")
    public ApiResponse<List<ReportTemplateDTO>> getReportTemplates(
            @RequestParam(required = false) String templateType) {
        return ApiResponse.success(reportService.getReportTemplates(templateType));
    }

    @PostMapping("/generate")
    public ApiResponse<ReportRecordDTO> generateReport(@RequestBody Map<String, String> body) {
        return ApiResponse.success(reportService.generateReport(
                body.get("templateCode"),
                body.get("startDate"),
                body.get("endDate"),
                body.get("zoneCode"),
                body.getOrDefault("fileFormat", "PDF"),
                body.getOrDefault("generatedBy", "SYSTEM")));
    }

    @GetMapping("/records")
    public ApiResponse<List<ReportRecordDTO>> getReportRecords(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResponse.success(reportService.getReportRecords(
                reportType, status, startDate, endDate));
    }

    @GetMapping("/records/{reportNo}")
    public ApiResponse<ReportRecordDTO> getReportRecord(@PathVariable String reportNo) {
        return ApiResponse.success(reportService.getReportRecord(reportNo));
    }

    @PostMapping("/records/{id}/email")
    public ApiResponse<Void> sendReportByEmail(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        reportService.sendReportByEmail(id, body.get("recipients"));
        return ApiResponse.success();
    }

    @GetMapping("/trend-alerts")
    public ApiResponse<List<TrendAnalysisDTO>> getTrendAlerts(
            @RequestParam(required = false) String sensorType,
            @RequestParam(required = false) String zoneCode,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(trendAnalysisService.getTrendAlerts(sensorType, zoneCode, status));
    }

    @PutMapping("/trend-alerts/{alertNo}/acknowledge")
    public ApiResponse<TrendAlert> acknowledgeTrendAlert(
            @PathVariable String alertNo,
            @RequestBody Map<String, String> body) {
        return ApiResponse.success(trendAnalysisService.acknowledgeTrendAlert(
                alertNo, body.get("acknowledgedBy")));
    }

    @GetMapping("/trend-rules")
    public ApiResponse<List<TrendRuleDTO>> getTrendRules() {
        return ApiResponse.success(trendAnalysisService.getTrendRules());
    }

    @PostMapping("/trend-rules")
    public ApiResponse<TrendRuleDTO> saveTrendRule(@RequestBody TrendRuleDTO dto) {
        return ApiResponse.success(trendAnalysisService.saveTrendRule(dto));
    }

    @PostMapping("/trend-check/run")
    public ApiResponse<Void> runTrendCheck() {
        trendAnalysisService.executeTrendCheck();
        return ApiResponse.success();
    }
}
