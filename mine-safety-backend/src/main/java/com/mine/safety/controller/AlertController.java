package com.mine.safety.controller;

import com.mine.safety.domain.Alert;
import com.mine.safety.domain.AlertRule;
import com.mine.safety.dto.ApiResponse;
import com.mine.safety.repository.AlertRepository;
import com.mine.safety.repository.AlertRuleRepository;
import com.mine.safety.service.AlertService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    public ApiResponse<IPage<Alert>> getAlerts(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String sensorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<Alert> alerts;

        if (status != null) {
            alerts = alertRepository.selectPage(new Page<>(page, size),
                    new LambdaQueryWrapper<Alert>().eq(Alert::getStatus, status).orderByDesc(Alert::getCreatedAt));
        } else if (level != null) {
            alerts = alertRepository.selectPage(new Page<>(page, size),
                    new LambdaQueryWrapper<Alert>().eq(Alert::getLevel, level).orderByDesc(Alert::getCreatedAt));
        } else if (sensorId != null) {
            alerts = alertRepository.selectPage(new Page<>(page, size),
                    new LambdaQueryWrapper<Alert>().eq(Alert::getSensorId, sensorId).orderByDesc(Alert::getCreatedAt));
        } else {
            alerts = alertRepository.selectPage(new Page<>(page, size), null);
        }

        return ApiResponse.success(alerts);
    }

    @GetMapping("/{alertNo}")
    public ApiResponse<Alert> getAlertByNo(@PathVariable String alertNo) {
        Alert alert = alertRepository.selectOne(
                new LambdaQueryWrapper<Alert>().eq(Alert::getAlertNo, alertNo));
        if (alert == null) {
            return ApiResponse.error(404, "报警不存在");
        }
        return ApiResponse.success(alert);
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
        return ApiResponse.success(alertRepository.selectList(
                new LambdaQueryWrapper<Alert>().orderByDesc(Alert::getCreatedAt).last("LIMIT 10")));
    }

    @GetMapping("/rules")
    public ApiResponse<List<AlertRule>> getAlertRules(
            @RequestParam(required = false) Integer enabled) {
        List<AlertRule> rules;
        if (enabled != null) {
            rules = alertRuleRepository.selectList(
                    new LambdaQueryWrapper<AlertRule>().eq(AlertRule::getEnabled, enabled));
        } else {
            rules = alertRuleRepository.selectList(null);
        }
        return ApiResponse.success(rules);
    }

    @PostMapping("/rules")
    public ApiResponse<AlertRule> createAlertRule(@RequestBody AlertRule rule) {
        alertRuleRepository.insert(rule);
        return ApiResponse.success(rule);
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<AlertRule> updateAlertRule(
            @PathVariable Long id,
            @RequestBody AlertRule rule) {
        rule.setId(id);
        alertRuleRepository.updateById(rule);
        return ApiResponse.success(rule);
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
