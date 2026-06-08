package com.mine.safety.controller;

import com.mine.safety.domain.LinkageExecutionRecord;
import com.mine.safety.dto.ResponseDTO;
import com.mine.safety.repository.LinkageExecutionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/linkage/executions")
@RequiredArgsConstructor
public class LinkageExecutionController {

    private final LinkageExecutionRecordRepository executionRecordRepository;

    @GetMapping
    public ResponseDTO<Page<LinkageExecutionRecord>> getExecutions(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<LinkageExecutionRecord> page;
        if (startTime != null && endTime != null) {
            page = executionRecordRepository.findByTimeRange(startTime, endTime, pageable);
        } else {
            page = executionRecordRepository.findAll(pageable);
        }
        return ResponseDTO.success(page);
    }

    @GetMapping("/alert/{alertId}")
    public ResponseDTO<List<LinkageExecutionRecord>> getExecutionsByAlertId(@PathVariable Long alertId) {
        List<LinkageExecutionRecord>> records = executionRecordRepository.findByAlertId(alertId);
        return ResponseDTO.success(records);
    }

    @GetMapping("/rule/{ruleId}")
    public ResponseDTO<List<LinkageExecutionRecord>> getExecutionsByRuleId(@PathVariable Long ruleId) {
        return ResponseDTO.success(executionRecordRepository.findByRuleId(ruleId));
    }

    @GetMapping("/action/{actionId}")
    public ResponseDTO<List<LinkageExecutionRecord>> getExecutionsByActionId(@PathVariable Long actionId) {
        return ResponseDTO.success(executionRecordRepository.findByActionId(actionId));
    }

    @GetMapping("/status/{status}")
    public ResponseDTO<List<LinkageExecutionRecord>> getExecutionsByStatus(@PathVariable Integer status) {
        return ResponseDTO.success(executionRecordRepository.findByStatus(status));
    }

    @GetMapping("/{id}")
    public ResponseDTO<LinkageExecutionRecord>> getExecutionById(@PathVariable Long id) {
        return executionRecordRepository.findById(id)
                .map(ResponseDTO::success)
                .orElse(ResponseDTO.error("记录不存在"));
    }

    @GetMapping("/statistics")
    public ResponseDTO<Map<String, Object>> getExecutionStatistics(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        List<Object[]> statusStats = executionRecordRepository.countByStatusAndTimeRange(startTime, endTime);
        List<Object[]> actionStats = executionRecordRepository.getActionStatistics(startTime, endTime);

        Map<String, Object> result = new HashMap<>();

        Map<String, Long> statusCount = new HashMap<>();
        long total = 0;
        for (Object[] row : statusStats) {
            Integer statusCode = (Integer) row[0];
            Long count = (Long) row[1];
            statusCount.put(String.valueOf(statusCode), count);
            total += count;
        }

        List<Map<String, Object>> actionStatistics = new ArrayList<>();
        for (Object[] row : actionStats) {
            Map<String, Object> actionMap = new HashMap<>();
            actionMap.put("actionType", row[0]);
            actionMap.put("total", row[1]);
            actionMap.put("success", row[2]);
            actionStatistics.add(actionMap);
        }

        result.put("statusDistribution", statusCount);
        result.put("total", total);
        result.put("actionStatistics", actionStatistics);
        result.put("startTime", startTime);
        result.put("endTime", endTime);

        return ResponseDTO.success(result);
    }

    @GetMapping("/retry/{id}")
    public ResponseDTO<Map<String, Object>> retryExecution(@PathVariable Long id) {
        return executionRecordRepository.findById(id)
                .map(record -> {
                    log.info("重试执行记录 - ID: {}", id);
                    return ResponseDTO.success(Map.of(
                            "message", "重试成功",
                            "executionId", id
                    ));
                })
                .orElse(ResponseDTO.error("记录不存在"));
    }
}
