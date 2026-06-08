package com.mine.safety.service;

import com.mine.safety.domain.Alert;
import com.mine.safety.domain.AlertRule;
import com.mine.safety.domain.Sensor;
import com.mine.safety.dto.AlertDTO;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.repository.AlertRepository;
import com.mine.safety.repository.AlertRuleRepository;
import com.mine.safety.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final SensorRepository sensorRepository;
    private final KafkaProducerService kafkaProducerService;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.alert.cooldown-seconds:30}")
    private int cooldownSeconds;

    private final Map<String, LocalDateTime> lastAlertTime = new ConcurrentHashMap<>();
    private final Map<String, List<BigDecimal>> valueHistory = new ConcurrentHashMap<>();

    @Transactional
    public void checkAndTriggerAlert(SensorDataDTO dto) {
        String sensorId = dto.getSensorId();
        String sensorType = dto.getSensorType();
        BigDecimal value = dto.getValue();

        List<AlertRule> rules = alertRuleRepository.findMatchingRules(sensorType, sensorId);

        for (AlertRule rule : rules) {
            if (rule.getEnabled() != 1) continue;

            boolean conditionMet = checkCondition(rule, value);
            if (!conditionMet) {
                clearHistory(sensorId, rule.getId());
                continue;
            }

            boolean durationMet = checkDuration(sensorId, rule.getId(), rule.getDuration(), value);
            if (!durationMet) {
                continue;
            }

            if (!checkCooldown(sensorId, rule.getId())) {
                continue;
            }

            triggerAlert(dto, rule, value);
        }
    }

    private boolean checkCondition(AlertRule rule, BigDecimal value) {
        BigDecimal threshold = rule.getThresholdValue();
        String conditionType = rule.getConditionType();

        return switch (AlertRule.ConditionType.valueOf(conditionType)) {
            case GT -> value.compareTo(threshold) > 0;
            case GTE -> value.compareTo(threshold) >= 0;
            case LT -> value.compareTo(threshold) < 0;
            case LTE -> value.compareTo(threshold) <= 0;
            case BETWEEN -> value.compareTo(threshold) >= 0 && value.compareTo(rule.getThresholdValueMax()) <= 0;
        };
    }

    private boolean checkDuration(String sensorId, Long ruleId, int durationSeconds, BigDecimal value) {
        if (durationSeconds <= 0) return true;

        String key = sensorId + ":" + ruleId;
        List<BigDecimal> history = valueHistory.computeIfAbsent(key, k -> new java.util.ArrayList<>());

        history.add(value);

        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(durationSeconds);

        long validCount = history.size();

        if (validCount >= durationSeconds) {
            history.clear();
            return true;
        }

        return false;
    }

    private void clearHistory(String sensorId, Long ruleId) {
        String key = sensorId + ":" + ruleId;
        valueHistory.remove(key);
    }

    private boolean checkCooldown(String sensorId, Long ruleId) {
        String key = sensorId + ":" + ruleId;
        LocalDateTime lastTime = lastAlertTime.get(key);

        if (lastTime != null) {
            if (LocalDateTime.now().isBefore(lastTime.plusSeconds(cooldownSeconds))) {
                return false;
            }
        }

        lastAlertTime.put(key, LocalDateTime.now());
        return true;
    }

    @Transactional
    public Alert triggerAlert(SensorDataDTO dto, AlertRule rule, BigDecimal value) {
        String sensorId = dto.getSensorId();

        Alert existing = alertRepository.findActiveAlert(sensorId, rule.getId());
        if (existing != null) {
            alertRepository.updateAlertFrequency(existing.getId(), dto.getTimestamp());
            log.debug("更新报警频率 - 传感器: {}, 规则: {}", sensorId, rule.getRuleName());
            return existing;
        }

        Sensor sensor = sensorRepository.findBySensorId(sensorId).orElse(null);

        Alert alert = new Alert();
        alert.setAlertNo(generateAlertNo());
        alert.setSensorId(sensorId);
        alert.setSensorName(sensor != null ? sensor.getName() : sensorId);
        alert.setSensorType(dto.getSensorType());
        alert.setLocation(dto.getLocation());
        alert.setAlertValue(value);
        alert.setThresholdValue(rule.getThresholdValue());
        alert.setLevel(rule.getLevel());
        alert.setRuleId(rule.getId());
        alert.setRuleName(rule.getRuleName());
        alert.setDescription(rule.getDescription());
        alert.setStatus(Alert.AlertStatus.PENDING.getValue());
        alert.setFirstAlertTime(dto.getTimestamp());
        alert.setLastAlertTime(dto.getTimestamp());
        alert.setAlertCount(1);

        alert = alertRepository.save(alert);

        AlertDTO alertDTO = convertToDTO(alert);
        alertDTO.setNotificationChannels(rule.getNotificationChannels());

        kafkaProducerService.sendAlertEvent(alertDTO);

        log.warn("触发报警 - 编号: {}, 传感器: {}, 类型: {}, 值: {}, 级别: {}",
                alert.getAlertNo(), sensorId, dto.getSensorType(), value, rule.getLevel());

        return alert;
    }

    @Async
    public void processAlertNotification(AlertDTO alert) {
        try {
            String channels = alert.getNotificationChannels();
            if (channels == null) return;

            for (String channel : channels.split(",")) {
                switch (AlertRule.NotificationChannel.valueOf(channel.trim())) {
                    case SMS -> sendSmsNotification(alert);
                    case EMAIL -> sendEmailNotification(alert);
                    case VOICE -> sendVoiceNotification(alert);
                    case WEBHOOK -> sendWebhookNotification(alert);
                }
            }

            String cacheKey = "alert:notified:" + alert.getAlertNo();
            redisTemplate.opsForValue().set(cacheKey, "1", 24, TimeUnit.HOURS);

            log.info("报警通知已处理完成 - 报警编号: {}", alert.getAlertNo());
        } catch (Exception e) {
            log.error("处理报警通知失败: {}", e.getMessage());
        }
    }

    private void sendSmsNotification(AlertDTO alert) {
        log.info("【短信通知: {}", alert);
    }

    private void sendEmailNotification(AlertDTO alert) {
        log.info("邮件通知: {}", alert);
    }

    private void sendVoiceNotification(AlertDTO alert) {
        log.info("语音通知: {}", alert);
    }

    private void sendWebhookNotification(AlertDTO alert) {
        log.info("Webhook通知: {}", alert);
    }

    @Transactional
    public Alert acknowledgeAlert(String alertNo, Integer status, String operator, String comment) {
        Alert alert = alertRepository.findByAlertNo(alertNo)
                .orElseThrow(() -> new RuntimeException("报警不存在: " + alertNo));

        alert.setStatus(status);
        alert.setAcknowledgedBy(operator);
        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.setAcknowledgedComment(comment);

        return alertRepository.save(alert);
    }

    private String generateAlertNo() {
        return "ALT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private AlertDTO convertToDTO(Alert alert) {
        AlertDTO dto = new AlertDTO();
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

    public Map<String, Object> getAlertStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        stats.put("pendingCount", alertRepository.countByStatus(Alert.AlertStatus.PENDING.getValue()));
        stats.put("processingCount", alertRepository.countByStatus(Alert.AlertStatus.PROCESSING.getValue()));
        stats.put("emergencyToday", alertRepository.countByLevelAndTimeAfter("EMERGENCY", today));
        stats.put("alertToday", alertRepository.countByLevelAndTimeAfter("ALERT", today));
        stats.put("warningToday", alertRepository.countByLevelAndTimeAfter("WARNING", today));

        return stats;
    }
}
