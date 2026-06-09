package com.mine.safety.service;

import com.mine.safety.domain.Alert;
import com.mine.safety.domain.Alert.AlertStatus;
import com.mine.safety.domain.Alert.EscalationLevel;
import com.mine.safety.domain.AlertDisposalRecord;
import com.mine.safety.domain.AlertDisposalRecord.DisposalType;
import com.mine.safety.domain.AlertEscalationLog;
import com.mine.safety.dto.AlertDTO;
import com.mine.safety.repository.AlertDisposalRecordRepository;
import com.mine.safety.repository.AlertEscalationLogRepository;
import com.mine.safety.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertLifecycleService {

    private final AlertRepository alertRepository;
    private final AlertDisposalRecordRepository disposalRecordRepository;
    private final AlertEscalationLogRepository escalationLogRepository;
    private final StringRedisTemplate redisTemplate;
    private final WebSocketPushService webSocketPushService;
    private final MessagePushService messagePushService;

    private static final String REDIS_UNCONFIRMED_QUEUE = "alert:unconfirmed:queue";
    private static final String REDIS_ALERT_KEY_PREFIX = "alert:lifecycle:";

    @Value("${app.alert.escalation.timeout-minutes:2}")
    private int escalationTimeoutMinutes;

    @Transactional
    public Alert triggerAlert(String sensorId, String sensorName, String sensorType,
                              String location, String tunnel, BigDecimal alertValue,
                              BigDecimal thresholdValue, String thresholdType,
                              String level, Long ruleId, String ruleName, String description) {
        Alert alert = new Alert();
        alert.setAlertNo(generateAlertNo());
        alert.setSensorId(sensorId);
        alert.setSensorName(sensorName);
        alert.setSensorType(sensorType);
        alert.setLocation(location);
        alert.setTunnel(tunnel);
        alert.setAlertValue(alertValue);
        alert.setThresholdValue(thresholdValue);
        alert.setThresholdType(thresholdType);
        alert.setLevel(level);
        alert.setRuleId(ruleId);
        alert.setRuleName(ruleName);
        alert.setDescription(description);
        alert.setStatus(AlertStatus.PENDING.getValue());
        alert.setEscalationLevel(EscalationLevel.DUTY.getValue());
        alert.setFirstAlertTime(LocalDateTime.now());
        alert.setLastAlertTime(LocalDateTime.now());
        alert.setAlertCount(1);

        alert = alertRepository.save(alert);

        addToUnconfirmedQueue(alert.getAlertNo(), alert.getLevel());

        AlertDTO dto = convertToDTO(alert);
        webSocketPushService.pushAlert(dto);

        log.info("触发报警 - 编号: {}, 传感器: {}, 级别: {}, 升级层级: {}",
                alert.getAlertNo(), sensorId, level, EscalationLevel.DUTY.getValue());

        return alert;
    }

    @Transactional
    public Alert confirmAlert(String alertNo, String confirmedBy) {
        Alert alert = alertRepository.findByAlertNo(alertNo)
                .orElseThrow(() -> new RuntimeException("报警不存在: " + alertNo));

        if (alert.getStatus() != AlertStatus.PENDING.getValue()) {
            throw new RuntimeException("报警状态不是触发状态，无法确认");
        }

        alert.setStatus(AlertStatus.CONFIRMED.getValue());
        alert.setConfirmedBy(confirmedBy);
        alert.setConfirmedAt(LocalDateTime.now());
        alert = alertRepository.save(alert);

        removeFromUnconfirmedQueue(alertNo);

        AlertDisposalRecord record = new AlertDisposalRecord();
        record.setAlertNo(alertNo);
        record.setDisposalType(DisposalType.CONFIRM.name());
        record.setDisposalMeasures("值班人员确认报警");
        record.setOperator(confirmedBy);
        record.setOperatorRole("DUTY");
        disposalRecordRepository.save(record);

        AlertDTO dto = convertToDTO(alert);
        webSocketPushService.pushAlert(dto);

        log.info("报警已确认 - 编号: {}, 确认人: {}", alertNo, confirmedBy);
        return alert;
    }

    @Transactional
    public Alert startProcessing(String alertNo, String processingBy) {
        Alert alert = alertRepository.findByAlertNo(alertNo)
                .orElseThrow(() -> new RuntimeException("报警不存在: " + alertNo));

        if (alert.getStatus() != AlertStatus.CONFIRMED.getValue() && alert.getStatus() != AlertStatus.PENDING.getValue()) {
            throw new RuntimeException("报警状态不允许进入处置中");
        }

        alert.setStatus(AlertStatus.PROCESSING.getValue());
        alert.setProcessingBy(processingBy);
        alert.setProcessingAt(LocalDateTime.now());
        alert = alertRepository.save(alert);

        AlertDisposalRecord record = new AlertDisposalRecord();
        record.setAlertNo(alertNo);
        record.setDisposalType(DisposalType.PROCESS.name());
        record.setDisposalMeasures("开始处置报警");
        record.setOperator(processingBy);
        disposalRecordRepository.save(record);

        AlertDTO dto = convertToDTO(alert);
        webSocketPushService.pushAlert(dto);

        log.info("报警处置中 - 编号: {}, 处置人: {}", alertNo, processingBy);
        return alert;
    }

    @Transactional
    public Alert recoverAlert(String alertNo, BigDecimal recoveryValue, LocalDateTime recoveryTime) {
        Alert alert = alertRepository.findByAlertNo(alertNo)
                .orElseThrow(() -> new RuntimeException("报警不存在: " + alertNo));

        if (alert.getStatus() != AlertStatus.PROCESSING.getValue() && alert.getStatus() != AlertStatus.CONFIRMED.getValue()) {
            throw new RuntimeException("报警状态不允许标记恢复");
        }

        alert.setStatus(AlertStatus.RECOVERED.getValue());
        alert.setRecoveredAt(recoveryTime != null ? recoveryTime : LocalDateTime.now());
        alert = alertRepository.save(alert);

        AlertDisposalRecord record = new AlertDisposalRecord();
        record.setAlertNo(alertNo);
        record.setDisposalType(DisposalType.RECOVER.name());
        record.setDisposalMeasures("传感器数值恢复到正常范围");
        record.setRecoveryValue(recoveryValue);
        record.setRecoveryTime(alert.getRecoveredAt());
        record.setOperator(alert.getProcessingBy() != null ? alert.getProcessingBy() : alert.getConfirmedBy());
        disposalRecordRepository.save(record);

        AlertDTO dto = convertToDTO(alert);
        webSocketPushService.pushAlert(dto);

        log.info("报警已恢复 - 编号: {}, 恢复值: {}", alertNo, recoveryValue);
        return alert;
    }

    @Transactional
    public Alert closeAlert(String alertNo, String closedBy, String closingMeasures,
                            String imageUrls, String remark) {
        Alert alert = alertRepository.findByAlertNo(alertNo)
                .orElseThrow(() -> new RuntimeException("报警不存在: " + alertNo));

        if (alert.getStatus() != AlertStatus.RECOVERED.getValue() && alert.getStatus() != AlertStatus.PROCESSING.getValue()) {
            throw new RuntimeException("报警状态不允许关闭");
        }

        alert.setStatus(AlertStatus.CLOSED.getValue());
        alert.setClosedBy(closedBy);
        alert.setClosedAt(LocalDateTime.now());
        alert = alertRepository.save(alert);

        AlertDisposalRecord record = new AlertDisposalRecord();
        record.setAlertNo(alertNo);
        record.setDisposalType(DisposalType.CLOSE.name());
        record.setDisposalMeasures(closingMeasures);
        record.setImageUrls(imageUrls);
        record.setOperator(closedBy);
        record.setRemark(remark);
        disposalRecordRepository.save(record);

        removeFromUnconfirmedQueue(alertNo);

        AlertDTO dto = convertToDTO(alert);
        webSocketPushService.pushAlert(dto);

        log.info("报警已关闭 - 编号: {}, 关闭人: {}", alertNo, closedBy);
        return alert;
    }

    @Transactional
    public AlertDisposalRecord addDisposalRecord(String alertNo, String disposalMeasures,
                                                  String imageUrls, String operator,
                                                  String operatorRole, String remark) {
        Alert alert = alertRepository.findByAlertNo(alertNo)
                .orElseThrow(() -> new RuntimeException("报警不存在: " + alertNo));

        AlertDisposalRecord record = new AlertDisposalRecord();
        record.setAlertNo(alertNo);
        record.setDisposalType(DisposalType.PROCESS.name());
        record.setDisposalMeasures(disposalMeasures);
        record.setImageUrls(imageUrls);
        record.setOperator(operator);
        record.setOperatorRole(operatorRole);
        record.setRemark(remark);
        return disposalRecordRepository.save(record);
    }

    @Transactional
    public int escalateAlert(Alert alert, EscalationLevel fromLevel, EscalationLevel toLevel) {
        int updated = alertRepository.updateEscalationLevel(
                alert.getAlertNo(), toLevel.getValue(), LocalDateTime.now());

        if (updated > 0) {
            AlertEscalationLog logEntry = new AlertEscalationLog();
            logEntry.setAlertNo(alert.getAlertNo());
            logEntry.setFromLevel(fromLevel.getValue());
            logEntry.setToLevel(toLevel.getValue());
            logEntry.setEscalationReason("超过" + escalationTimeoutMinutes + "分钟未确认，自动升级");
            logEntry.setNotificationChannels("WECHAT_WORK,SMS,VOICE");
            escalationLogRepository.save(logEntry);

            Map<String, Object> params = Map.of(
                    "channels", List.of("WECHAT_WORK", "SMS", "VOICE"),
                    "urgent", true
            );
            messagePushService.pushAlertMessage(convertToDTO(alert), params);

            log.warn("报警升级 - 编号: {}, {} -> {}", alert.getAlertNo(), fromLevel.getValue(), toLevel.getValue());
        }

        return updated;
    }

    public List<Alert> getAlertsForEscalation() {
        LocalDateTime thresholdTime = LocalDateTime.now().minusMinutes(escalationTimeoutMinutes);
        List<Alert> result = new ArrayList<>();

        List<Alert> dutyAlerts = alertRepository.findUnconfirmedBeforeTime(
                EscalationLevel.DUTY.getValue(), thresholdTime);
        result.addAll(dutyAlerts);

        LocalDateTime shiftLeaderThreshold = LocalDateTime.now().minusMinutes(escalationTimeoutMinutes * 2);
        List<Alert> shiftLeaderAlerts = alertRepository.findUnconfirmedBeforeTime(
                EscalationLevel.SHIFT_LEADER.getValue(), shiftLeaderThreshold);
        result.addAll(shiftLeaderAlerts);

        return result;
    }

    public Page<Alert> searchAlerts(Integer status, String level, String sensorId,
                                     String tunnel, String sensorType,
                                     LocalDateTime startTime, LocalDateTime endTime,
                                     int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (tunnel != null && startTime != null && endTime != null) {
            return alertRepository.findByTunnelAndFirstAlertTimeBetweenOrderByCreatedAtDesc(tunnel, startTime, endTime, pageable);
        }
        if (sensorType != null && startTime != null && endTime != null) {
            return alertRepository.findBySensorTypeAndFirstAlertTimeBetweenOrderByCreatedAtDesc(sensorType, startTime, endTime, pageable);
        }
        if (startTime != null && endTime != null) {
            return alertRepository.findByFirstAlertTimeBetweenOrderByCreatedAtDesc(startTime, endTime, pageable);
        }
        if (status != null) {
            return alertRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        if (level != null) {
            return alertRepository.findByLevelOrderByCreatedAtDesc(level, pageable);
        }
        if (sensorId != null) {
            return alertRepository.findBySensorIdOrderByCreatedAtDesc(sensorId, pageable);
        }
        return alertRepository.findAll(pageable);
    }

    public Map<String, Object> getAlertStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();

        if (startTime == null) {
            startTime = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        stats.put("pendingCount", alertRepository.countByStatus(AlertStatus.PENDING.getValue()));
        stats.put("confirmedCount", alertRepository.countByStatus(AlertStatus.CONFIRMED.getValue()));
        stats.put("processingCount", alertRepository.countByStatus(AlertStatus.PROCESSING.getValue()));
        stats.put("recoveredCount", alertRepository.countByStatus(AlertStatus.RECOVERED.getValue()));
        stats.put("closedCount", alertRepository.countByStatus(AlertStatus.CLOSED.getValue()));

        List<Object[]> tunnelStats = alertRepository.countByTunnelBetween(startTime, endTime);
        Map<String, Long> byTunnel = new HashMap<>();
        for (Object[] row : tunnelStats) {
            byTunnel.put(String.valueOf(row[0]), (Long) row[1]);
        }
        stats.put("byTunnel", byTunnel);

        List<Object[]> levelStats = alertRepository.countByLevelBetween(startTime, endTime);
        Map<String, Long> byLevel = new HashMap<>();
        for (Object[] row : levelStats) {
            byLevel.put(String.valueOf(row[0]), (Long) row[1]);
        }
        stats.put("byLevel", byLevel);

        List<Object[]> typeStats = alertRepository.countBySensorTypeBetween(startTime, endTime);
        Map<String, Long> bySensorType = new HashMap<>();
        for (Object[] row : typeStats) {
            bySensorType.put(String.valueOf(row[0]), (Long) row[1]);
        }
        stats.put("bySensorType", bySensorType);

        List<Object[]> dailyStats = alertRepository.countByDayBetween(startTime, endTime);
        Map<String, Long> byDay = new HashMap<>();
        for (Object[] row : dailyStats) {
            byDay.put(String.valueOf(row[0]), (Long) row[1]);
        }
        stats.put("byDay", byDay);

        stats.put("startTime", startTime);
        stats.put("endTime", endTime);

        return stats;
    }

    public List<AlertDisposalRecord> getDisposalRecords(String alertNo) {
        return disposalRecordRepository.findByAlertNoOrderByCreatedAtDesc(alertNo);
    }

    public List<AlertEscalationLog> getEscalationLogs(String alertNo) {
        return escalationLogRepository.findByAlertNoOrderByCreatedAtDesc(alertNo);
    }

    public List<Alert> getRealtimeUnconfirmedAlerts() {
        return alertRepository.findAllUnconfirmedAndConfirmed();
    }

    public List<Alert> exportAlerts(LocalDateTime startTime, LocalDateTime endTime,
                                     String tunnel, String sensorType) {
        if (tunnel != null) {
            return alertRepository.findByTunnelAndFirstAlertTimeBetweenOrderByCreatedAtDesc(
                    tunnel, startTime, endTime, PageRequest.of(0, 10000)).getContent();
        }
        if (sensorType != null) {
            return alertRepository.findBySensorTypeAndFirstAlertTimeBetweenOrderByCreatedAtDesc(
                    sensorType, startTime, endTime, PageRequest.of(0, 10000)).getContent();
        }
        return alertRepository.findByFirstAlertTimeBetweenOrderByCreatedAtDesc(
                startTime, endTime, PageRequest.of(0, 10000)).getContent();
    }

    private void addToUnconfirmedQueue(String alertNo, String level) {
        try {
            String key = REDIS_ALERT_KEY_PREFIX + alertNo;
            Map<String, String> alertData = new HashMap<>();
            alertData.put("alertNo", alertNo);
            alertData.put("level", level);
            alertData.put("triggerTime", LocalDateTime.now().toString());
            alertData.put("escalationLevel", EscalationLevel.DUTY.getValue());

            redisTemplate.opsForHash().putAll(key, alertData);
            redisTemplate.expire(key, 24, TimeUnit.HOURS);

            double score = System.currentTimeMillis();
            redisTemplate.opsForZSet().add(REDIS_UNCONFIRMED_QUEUE, alertNo, score);

            log.debug("加入未确认报警队列 - 报警编号: {}", alertNo);
        } catch (Exception e) {
            log.warn("Redis操作失败，报警队列功能降级: {}", e.getMessage());
        }
    }

    private void removeFromUnconfirmedQueue(String alertNo) {
        try {
            String key = REDIS_ALERT_KEY_PREFIX + alertNo;
            redisTemplate.delete(key);
            redisTemplate.opsForZSet().remove(REDIS_UNCONFIRMED_QUEUE, alertNo);

            log.debug("从未确认报警队列移除 - 报警编号: {}", alertNo);
        } catch (Exception e) {
            log.warn("Redis操作失败: {}", e.getMessage());
        }
    }

    private String generateAlertNo() {
        return "ALT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private AlertDTO convertToDTO(Alert alert) {
        AlertDTO dto = new AlertDTO();
        dto.setId(alert.getId());
        dto.setAlertNo(alert.getAlertNo());
        dto.setSensorId(alert.getSensorId());
        dto.setSensorName(alert.getSensorName());
        dto.setSensorType(alert.getSensorType());
        dto.setLocation(alert.getLocation());
        dto.setAlertValue(alert.getAlertValue());
        dto.setThresholdValue(alert.getThresholdValue());
        dto.setLevel(alert.getLevel());
        dto.setRuleId(alert.getRuleId());
        dto.setRuleName(alert.getRuleName());
        dto.setDescription(alert.getDescription());
        dto.setStatus(alert.getStatus());
        dto.setAcknowledgedBy(alert.getAcknowledgedBy());
        dto.setAcknowledgedAt(alert.getAcknowledgedAt());
        dto.setAcknowledgedComment(alert.getAcknowledgedComment());
        dto.setFirstAlertTime(alert.getFirstAlertTime());
        dto.setLastAlertTime(alert.getLastAlertTime());
        dto.setAlertCount(alert.getAlertCount());
        return dto;
    }
}
