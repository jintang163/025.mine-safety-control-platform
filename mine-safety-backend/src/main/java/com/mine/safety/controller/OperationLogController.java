package com.mine.safety.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mine.safety.domain.SysOperationLog;
import com.mine.safety.dto.ApiResponse;
import com.mine.safety.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    @GetMapping
    public ApiResponse<IPage<SysOperationLog>> getOperationLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String operationModule,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        IPage<SysOperationLog> logs = operationLogService.getOperationLogs(
                username, operationType, operationModule, startTime, endTime, page, size);
        return ApiResponse.success(logs);
    }
}
