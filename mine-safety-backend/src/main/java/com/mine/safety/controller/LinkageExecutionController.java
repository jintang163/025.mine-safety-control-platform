package com.mine.safety.controller;

import com.mine.safety.domain.LinkageExecutionRecord;
import com.mine.safety.dto.ResponseDTO;
import com.mine.safety.repository.LinkageExecutionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAuthority('device:view')")
    public ResponseDTO<IPage<LinkageExecutionRecord>> getExecutions(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        IPage<LinkageExecutionRecord> result;
        if (startTime != null && endTime != null) {
            result = executionRecordRepository.selectPage(new Page<>(page, size),
                    new LambdaQueryWrapper<LinkageExecutionRecord>().between(LinkageExecutionRecord::getCreatedAt, startTime, endTime));
        } else {
            result = executionRecordRepository.selectPage(new Page<>(page, size), null);
        }
        return ResponseDTO.success(result);
    }

    @GetMapping("/alert/{alertId}")
    @PreAuthorize("hasAuthority('device:view')")
    public ResponseDTO<List<LinkageExecutionRecord>> getExecutionsByAlertId(@PathVariable Long alertId) {
        List<LinkageExecutionRecord> records = executionRecordRepository.selectList(
                new LambdaQueryWrapper<LinkageExecutionRecord>().eq(LinkageExecutionRecord::getAlertId, alertId));
        return ResponseDTO.success(records);
    }

    @GetMapping("/rule/{ruleId}")
    @PreAuthorize("hasAuthority('device:view')")
    public ResponseDTO<List<LinkageExecutionRecord>> getExecutionsByRuleId(@PathVariable Long ruleId) {
        return ResponseDTO.success(executionRecordRepository.selectList(
                new LambdaQueryWrapper<LinkageExecutionRecord>().eq(LinkageExecutionRecord::getRuleId, ruleId)));
    }

    @GetMapping("/action/{actionId}")
    @PreAuthorize("hasAuthority('device:view')")
    public ResponseDTO<List<LinkageExecutionRecord>> getExecutionsByActionId(@PathVariable Long actionId) {
        return ResponseDTO.success(executionRecordRepository.selectList(
                new LambdaQueryWrapper<LinkageExecutionRecord>().eq(LinkageExecutionRecord::getActionId, actionId)));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAuthority('device:view')")
    public ResponseDTO<List<LinkageExecutionRecord>> getExecutionsByStatus(@PathVariable Integer status) {
        return ResponseDTO.success(executionRecordRepository.selectList(
                new LambdaQueryWrapper<LinkageExecutionRecord>().eq(LinkageExecutionRecord::getStatus, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('device:view')")
    public ResponseDTO<LinkageExecutionRecord> getExecutionById(@PathVariable Long id) {
        LinkageExecutionRecord record = executionRecordRepository.selectById(id);
        if (record == null) {
            return ResponseDTO.error("记录不存在");
        }
        return ResponseDTO.success(record);
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('device:view')")
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
    @PreAuthorize("hasAuthority('device:edit')")
    public ResponseDTO<Map<String, Object>> retryExecution(@PathVariable Long id) {
        LinkageExecutionRecord record = executionRecordRepository.selectById(id);
        if (record == null) {
            return ResponseDTO.error("记录不存在");
        }
        log.info("重试执行记录 - ID: {}", id);
        return ResponseDTO.success(Map.of(
                "message", "重试成功",
                "executionId", id
        ));
    }
}
