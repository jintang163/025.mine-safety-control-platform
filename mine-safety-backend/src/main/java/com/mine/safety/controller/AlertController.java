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

/**
 * 报警管理控制器
 * 提供报警记录查询、报警确认、报警规则管理的REST API接口。
 *
 * 报警管理接口：
 *   - GET    /alerts                    分页查询报警记录
 *   - GET    /alerts/{alertNo}          查询单条报警详情
 *   - POST   /alerts/{alertNo}/acknowledge 确认/处理报警
 *   - GET    /alerts/statistics         获取报警统计数据
 *   - GET    /alerts/recent             获取最近10条报警
 *
 * 报警规则管理接口：
 *   - GET    /alerts/rules              查询报警规则列表
 *   - POST   /alerts/rules              创建报警规则
 *   - PUT    /alerts/rules/{id}         更新报警规则
 *   - DELETE /alerts/rules/{id}         删除报警规则
 *
 * 其他接口：
 *   - POST   /alerts/webhook            接收第三方系统报警Webhook
 *
 * @author mine-safety
 * @since 1.0.0
 */
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    /** 报警服务 */
    private final AlertService alertService;

    /** 报警记录Repository */
    private final AlertRepository alertRepository;

    /** 报警规则Repository */
    private final AlertRuleRepository alertRuleRepository;

    /**
     * 分页查询报警记录
     * 支持按状态、级别、传感器ID过滤，按创建时间倒序排列。
     *
     * @param status   状态过滤（可选，0-未处理，1-处理中，2-已处理，3-已忽略）
     * @param level    级别过滤（可选，INFO/WARNING/ALERT/EMERGENCY）
     * @param sensorId 传感器ID过滤（可选）
     * @param page     页码（默认0）
     * @param size     每页大小（默认20）
     * @return 报警记录分页结果
     */
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

    /**
     * 查询单条报警详情
     *
     * @param alertNo 报警编号
     * @return 报警详情（不存在时返回404）
     */
    @GetMapping("/{alertNo}")
    public ApiResponse<Alert> getAlertByNo(@PathVariable String alertNo) {
        return alertRepository.findByAlertNo(alertNo)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "报警不存在"));
    }

    /**
     * 确认/处理报警
     * 更新报警状态、处理人、处理时间和备注。
     *
     * @param alertNo 报警编号
     * @param request 确认请求（包含状态、处理人、备注）
     * @return 更新后的报警
     */
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

    /**
     * 获取报警统计数据
     * 包括未处理报警数、今日报警数、各级别报警数等。
     *
     * @return 统计数据Map
     */
    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getAlertStatistics() {
        return ApiResponse.success(alertService.getAlertStatistics());
    }

    /**
     * 获取最近10条报警记录
     * 用于首页展示。
     *
     * @return 最近10条报警列表
     */
    @GetMapping("/recent")
    public ApiResponse<List<Alert>> getRecentAlerts() {
        return ApiResponse.success(alertRepository.findTop10ByOrderByCreatedAtDesc());
    }

    /**
     * 查询报警规则列表
     * 支持按启用状态过滤。
     *
     * @param enabled 是否启用（可选，0-禁用，1-启用）
     * @return 报警规则列表
     */
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

    /**
     * 创建报警规则
     *
     * @param rule 报警规则
     * @return 创建后的报警规则
     */
    @PostMapping("/rules")
    public ApiResponse<AlertRule> createAlertRule(@RequestBody AlertRule rule) {
        return ApiResponse.success(alertRuleRepository.save(rule));
    }

    /**
     * 更新报警规则
     *
     * @param id    规则ID
     * @param rule  更新的规则信息
     * @return 更新后的报警规则
     */
    @PutMapping("/rules/{id}")
    public ApiResponse<AlertRule> updateAlertRule(
            @PathVariable Long id,
            @RequestBody AlertRule rule) {
        rule.setId(id);
        return ApiResponse.success(alertRuleRepository.save(rule));
    }

    /**
     * 删除报警规则
     *
     * @param id 规则ID
     * @return 空响应
     */
    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteAlertRule(@PathVariable Long id) {
        alertRuleRepository.deleteById(id);
        return ApiResponse.success();
    }

    /**
     * 接收第三方系统报警Webhook
     * 用于与其他报警系统集成。
     *
     * @param payload Webhook请求体
     * @return 空响应
     */
    @PostMapping("/webhook")
    public ApiResponse<Void> webhookReceiver(@RequestBody Map<String, Object> payload) {
        System.out.println("收到Webhook报警: " + payload);
        return ApiResponse.success();
    }

    /**
     * 报警确认请求DTO
     * 包含确认报警所需的参数。
     */
    @Data
    public static class AcknowledgeRequest {
        /** 新状态（2-已处理，3-已忽略） */
        private Integer status;
        /** 处理人 */
        private String operator;
        /** 处理备注 */
        private String comment;
    }
}
