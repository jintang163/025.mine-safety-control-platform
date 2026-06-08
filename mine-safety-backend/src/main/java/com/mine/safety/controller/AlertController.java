package com.mine.safety.controller;

import com.mine.safety.domain.Alert;
import com.mine.safety.domain.AlertRule;
import com.mine.safety.dto.AlertDTO;
import com.mine.safety.dto.ApiResponse;
import com.mine.safety.repository.AlertRepository;
import com.mine.safety.repository.AlertRuleRepository;
import com.mine.safety.service.AlertService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;

    @GetMapping
    public ApiResponse<Page<Alert>> getAlerts(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String sensorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Alert> alerts;

        if (status != null) {
            alerts = alertRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (level != null) {
            alerts = alertRepository.findByLevelOrderByCreatedAtDesc(level, pageable);
        } else if (sensorId != null) {
            alerts = alertRepository.findBySensorIdOrderByCreatedAtDesc(sensorId, pageable);
        } else {
            alerts = alertRepository.findAll(pageable);
        }

        return ApiResponse.success(alerts);
    }

    @GetMapping("/{alertNo}")
    public ApiResponse<Alert> getAlertByNo(@PathVariable String alertNo) {
        return alertRepository.findByAlertNo(alertNo)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "报警不存在"));
    }

    @PostMapping("/{alertNo}/acknowledge")
    public ApiResponse<Alert> acknowledgeAlert(
            @PathVariable String alertNo,
            @RequestBody AcknowledgeRequest request) {
        Alert alert = alertService.acknowledgeAlert(
                alertNo,
                request.getStatus(),
                request.getOperator(),
                request.getComment()
        );
        return ApiResponse.success(alert);
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getAlertStatistics() {
        return ApiResponse.success(alertService.getAlertStatistics());
    }

    @GetMapping("/recent")
    public ApiResponse<List<Alert>> getRecentAlerts() {
        return ApiResponse.success(alertRepository.findTop10ByOrderByCreatedAtDesc());
    }

    @GetMapping("/rules")
    public ApiResponse<List<AlertRule>> getAlertRules(
            @RequestParam(required = false) Integer enabled) {
        List<AlertRule> rules;
        if (enabled != null) {
            rules = alertRuleRepository.findByEnabled(enabled);
        } else {
            rules = alertRuleRepository.findAll();
        }
        return ApiResponse.success(rules);
    }

    @PostMapping("/rules")
    public ApiResponse<AlertRule> createAlertRule(@RequestBody AlertRule rule) {
        return ApiResponse.success(alertRuleRepository.save(rule));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<AlertRule> updateAlertRule(
            @PathVariable Long id,
            @RequestBody AlertRule rule) {
        rule.setId(id);
        return ApiResponse.success(alertRuleRepository.save(rule));
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteAlertRule(@PathVariable Long id) {
        alertRuleRepository.deleteById(id);
        return ApiResponse.success();
    }

    @PostMapping("/webhook")
    public ApiResponse<Void> webhookReceiver(@RequestBody Map<String, Object> payload) {
        System.out.println("收到Webhook报警: " + payload);
        return ApiResponse.success();
    }

    @Data
    public static class AcknowledgeRequest {
        private Integer status;
        private String operator;
        private String comment;
    }
}
