package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mine.safety.domain.SysOperationLog;
import com.mine.safety.repository.SysOperationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final SysOperationLogRepository operationLogRepository;

    @Value("${app.audit.enabled:true}")
    private boolean auditEnabled;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Async
    public void logOperation(Long userId, String username, String realName,
                             String operationType, String operationModule, String operationDesc,
                             HttpServletRequest request) {
        if (!auditEnabled) {
            return;
        }

        try {
            SysOperationLog logEntity = new SysOperationLog();
            logEntity.setOperationNo(generateOperationNo());
            logEntity.setUserId(userId);
            logEntity.setUsername(username);
            logEntity.setRealName(realName);
            logEntity.setOperationType(operationType);
            logEntity.setOperationModule(operationModule);
            logEntity.setOperationDesc(operationDesc);

            if (request != null) {
                logEntity.setRequestMethod(request.getMethod());
                logEntity.setRequestUrl(request.getRequestURI());
                logEntity.setIpAddress(getClientIp(request));
                logEntity.setUserAgent(request.getHeader("User-Agent"));
            }

            logEntity.setStatus(1);
            operationLogRepository.insert(logEntity);
        } catch (Exception e) {
            log.error("Failed to save operation log", e);
        }
    }

    @Async
    public void logOperationWithDetail(Long userId, String username, String realName,
                                       String operationType, String operationModule, String operationDesc,
                                       String targetId, String targetType,
                                       Object oldValue, Object newValue,
                                       HttpServletRequest request) {
        if (!auditEnabled) {
            return;
        }

        try {
            SysOperationLog logEntity = new SysOperationLog();
            logEntity.setOperationNo(generateOperationNo());
            logEntity.setUserId(userId);
            logEntity.setUsername(username);
            logEntity.setRealName(realName);
            logEntity.setOperationType(operationType);
            logEntity.setOperationModule(operationModule);
            logEntity.setOperationDesc(operationDesc);
            logEntity.setTargetId(targetId);
            logEntity.setTargetType(targetType);

            if (oldValue != null) {
                logEntity.setOldValue(truncate(JSON.toJSONString(oldValue), 2000));
            }
            if (newValue != null) {
                logEntity.setNewValue(truncate(JSON.toJSONString(newValue), 2000));
            }

            if (request != null) {
                logEntity.setRequestMethod(request.getMethod());
                logEntity.setRequestUrl(request.getRequestURI());
                logEntity.setIpAddress(getClientIp(request));
                logEntity.setUserAgent(request.getHeader("User-Agent"));
            }

            logEntity.setStatus(1);
            operationLogRepository.insert(logEntity);
        } catch (Exception e) {
            log.error("Failed to save operation log with detail", e);
        }
    }

    public IPage<SysOperationLog> getOperationLogs(String username, String operationType,
                                                    String operationModule,
                                                    LocalDateTime startTime, LocalDateTime endTime,
                                                    int page, int size) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysOperationLog> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

        if (username != null && !username.isEmpty()) {
            wrapper.like(SysOperationLog::getUsername, username);
        }
        if (operationType != null && !operationType.isEmpty()) {
            wrapper.eq(SysOperationLog::getOperationType, operationType);
        }
        if (operationModule != null && !operationModule.isEmpty()) {
            wrapper.eq(SysOperationLog::getOperationModule, operationModule);
        }
        if (startTime != null) {
            wrapper.ge(SysOperationLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysOperationLog::getCreatedAt, endTime);
        }

        wrapper.orderByDesc(SysOperationLog::getCreatedAt);
        return operationLogRepository.selectPage(new Page<>(page, size), wrapper);
    }

    private String generateOperationNo() {
        return "OP" + LocalDateTime.now().format(FORMATTER) +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    public HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
