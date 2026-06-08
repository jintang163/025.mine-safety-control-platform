package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.domain.Alert;
import com.mine.safety.domain.AlertRule;
import com.mine.safety.domain.Sensor;
import com.mine.safety.dto.AlertDTO;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.repository.AlertRepository;
import com.mine.safety.repository.AlertRuleRepository;
import com.mine.safety.repository.SensorRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 报警引擎服务
 * 负责报警规则匹配、持续时间检测、冷却机制和多渠道通知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    /**
     * 报警记录Repository
     */
    private final AlertRepository alertRepository;

    /**
     * 报警规则Repository
     */
    private final AlertRuleRepository alertRuleRepository;

    /**
     * 传感器Repository
     */
    private final SensorRepository sensorRepository;

    /**
     * Kafka消息生产者服务
     */
    private final KafkaProducerService kafkaProducerService;

    /**
     * Redis缓存模板
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 邮件发送器（配置spring.mail后启用）
     */
    private final JavaMailSender mailSender;

    /**
     * HTTP客户端，用于Webhook和短信API调用
     */
    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    /**
     * 报警冷却时间（秒），同一规则触发后在此时间内不再重复触发
     */
    @Value("${app.alert.cooldown-seconds:30}")
    private int cooldownSeconds;

    /**
     * 短信通知配置
     */
    @Value("${app.alert.notification.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${app.alert.notification.sms.api-url:}")
    private String smsApiUrl;

    @Value("${app.alert.notification.sms.api-key:}")
    private String smsApiKey;

    @Value("${app.alert.notification.sms.template-id:}")
    private String smsTemplateId;

    @Value("${app.alert.notification.sms.phone-numbers:}")
    private String smsPhoneNumbers;

    /**
     * 邮件通知配置
     */
    @Value("${app.alert.notification.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.alert.notification.email.from:}")
    private String emailFrom;

    @Value("${app.alert.notification.email.to:}")
    private String emailTo;

    /**
     * 语音通知配置
     */
    @Value("${app.alert.notification.voice.enabled:false}")
    private boolean voiceEnabled;

    @Value("${app.alert.notification.voice.api-url:}")
    private String voiceApiUrl;

    @Value("${app.alert.notification.voice.phone-numbers:}")
    private String voicePhoneNumbers;

    /**
     * Webhook通知配置
     */
    @Value("${app.alert.notification.webhook.enabled:true}")
    private boolean webhookEnabled;

    @Value("${app.alert.notification.webhook.urls:}")
    private List<String> webhookUrls;

    /**
     * 最后报警时间缓存，key: sensorId:ruleId
     */
    private final Map<String, LocalDateTime> lastAlertTime = new ConcurrentHashMap<>();

    /**
     * 历史值缓存（带时间戳），用于持续时间检测
     * key: sensorId:ruleId，value: 带时间戳的数值列表
     */
    private final Map<String, List<TimedValue>> valueHistory = new ConcurrentHashMap<>();

    /**
     * 带时间戳的数值对象
     * 用于存储历史数据及其采集时间，实现基于时间窗口的持续时间检测
     */
    @Data
    private static class TimedValue {
        /**
         * 传感器数值
         */
        private final BigDecimal value;

        /**
         * 采集时间戳
         */
        private final LocalDateTime timestamp;

        public TimedValue(BigDecimal value, LocalDateTime timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    /**
     * 检查并触发报警
     * 核心方法：对每条传感器数据进行规则匹配，满足条件则触发报警
     *
     * @param dto 传感器数据DTO
     */
    @Transactional
    public void checkAndTriggerAlert(SensorDataDTO dto) {
        String sensorId = dto.getSensorId();
        String sensorType = dto.getSensorType();
        BigDecimal value = dto.getValue();
        LocalDateTime timestamp = dto.getTimestamp();

        // 查询匹配的报警规则（按传感器类型或指定传感器ID）
        List<AlertRule> rules = alertRuleRepository.findMatchingRules(sensorType, sensorId);

        for (AlertRule rule : rules) {
            // 跳过禁用的规则
            if (rule.getEnabled() != 1) continue;

            // 步骤1：检查报警条件是否满足（阈值比较）
            boolean conditionMet = checkCondition(rule, value);
            if (!conditionMet) {
                // 条件不满足，清空该规则的历史记录
                clearHistory(sensorId, rule.getId());
                continue;
            }

            // 步骤2：检查持续时间条件（基于时间戳的时间窗口检测）
            boolean durationMet = checkDuration(sensorId, rule.getId(), rule.getDuration(), value, timestamp);
            if (!durationMet) {
                // 持续时间不满足，等待下一条数据继续累积
                continue;
            }

            // 步骤3：检查冷却机制，避免短时间内重复报警
            if (!checkCooldown(sensorId, rule.getId())) {
                continue;
            }

            // 所有条件满足，触发报警
            triggerAlert(dto, rule, value);
        }
    }

    /**
     * 检查报警条件（阈值比较）
     *
     * @param rule  报警规则
     * @param value 当前传感器数值
     * @return true-条件满足，false-不满足
     */
    private boolean checkCondition(AlertRule rule, BigDecimal value) {
        BigDecimal threshold = rule.getThresholdValue();
        String conditionType = rule.getConditionType();

        return switch (AlertRule.ConditionType.valueOf(conditionType)) {
            case GT -> value.compareTo(threshold) > 0;        // 大于
            case GTE -> value.compareTo(threshold) >= 0;       // 大于等于
            case LT -> value.compareTo(threshold) < 0;         // 小于
            case LTE -> value.compareTo(threshold) <= 0;       // 小于等于
            case BETWEEN ->                                    // 区间
                    value.compareTo(threshold) >= 0 &&
                    value.compareTo(rule.getThresholdValueMax()) <= 0;
        };
    }

    /**
     * 检查持续时间条件（基于时间戳的时间窗口检测）
     * 核心逻辑：检查在duration秒的时间窗口内，是否所有数据点都满足报警条件
     *
     * @param sensorId        传感器ID
     * @param ruleId          规则ID
     * @param durationSeconds 持续时间（秒）
     * @param value           当前数值
     * @param timestamp       当前时间戳
     * @return true-持续时间满足，false-不满足
     */
    private boolean checkDuration(String sensorId, Long ruleId, int durationSeconds,
                                  BigDecimal value, LocalDateTime timestamp) {
        // 持续时间为0表示立即触发，无需累积
        if (durationSeconds <= 0) return true;

        String key = sensorId + ":" + ruleId;
        List<TimedValue> history = valueHistory.computeIfAbsent(key, k -> new ArrayList<>());

        // 添加当前数据点（带时间戳）
        history.add(new TimedValue(value, timestamp));

        // 计算时间窗口的起始点
        LocalDateTime windowStart = timestamp.minusSeconds(durationSeconds);

        // 清理超出时间窗口的历史数据
        history.removeIf(tv -> tv.getTimestamp().isBefore(windowStart));

        // 检查：最早的历史数据是否在时间窗口内（即已累积足够长时间）
        // 同时要求历史记录数量大于0（防止边界情况）
        if (!history.isEmpty() &&
                !history.get(0).getTimestamp().isAfter(windowStart) &&
                history.size() >= 2) {
            // 清空历史，避免连续触发
            history.clear();
            return true;
        }

        return false;
    }

    /**
     * 清空指定传感器和规则的历史记录
     * 当报警条件不再满足时调用，重置持续时间累积
     *
     * @param sensorId 传感器ID
     * @param ruleId   规则ID
     */
    private void clearHistory(String sensorId, Long ruleId) {
        String key = sensorId + ":" + ruleId;
        valueHistory.remove(key);
    }

    /**
     * 检查冷却机制
     * 防止同一规则在短时间内重复触发报警
     *
     * @param sensorId 传感器ID
     * @param ruleId   规则ID
     * @return true-可以触发，false-在冷却期内
     */
    private boolean checkCooldown(String sensorId, Long ruleId) {
        String key = sensorId + ":" + ruleId;
        LocalDateTime lastTime = lastAlertTime.get(key);

        if (lastTime != null) {
            // 如果在冷却期内，不允许重复报警
            if (LocalDateTime.now().isBefore(lastTime.plusSeconds(cooldownSeconds))) {
                return false;
            }
        }

        // 更新最后报警时间
        lastAlertTime.put(key, LocalDateTime.now());
        return true;
    }

    /**
     * 触发报警
     * 创建报警记录、发送Kafka事件、记录日志
     *
     * @param dto   传感器数据
     * @param rule  触发的报警规则
     * @param value 报警值
     * @return 报警实体
     */
    @Transactional
    public Alert triggerAlert(SensorDataDTO dto, AlertRule rule, BigDecimal value) {
        String sensorId = dto.getSensorId();

        // 检查是否已有未处理的同规则报警，如果有则更新频率而不重复创建
        Alert existing = alertRepository.findActiveAlert(sensorId, rule.getId());
        if (existing != null) {
            alertRepository.updateAlertFrequency(existing.getId(), dto.getTimestamp());
            log.debug("更新报警频率 - 传感器: {}, 规则: {}", sensorId, rule.getRuleName());
            return existing;
        }

        // 查询传感器信息
        Sensor sensor = sensorRepository.findBySensorId(sensorId).orElse(null);

        // 创建新报警记录
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

        // 转换为DTO并设置通知渠道
        AlertDTO alertDTO = convertToDTO(alert);
        alertDTO.setNotificationChannels(rule.getNotificationChannels());

        // 发送报警事件到Kafka
        kafkaProducerService.sendAlertEvent(alertDTO);

        // 异步处理多渠道通知
        processAlertNotification(alertDTO);

        log.warn("触发报警 - 编号: {}, 传感器: {}, 类型: {}, 值: {}, 级别: {}",
                alert.getAlertNo(), sensorId, dto.getSensorType(), value, rule.getLevel());

        return alert;
    }

    /**
     * 异步处理报警通知
     * 根据配置的通知渠道发送多渠道报警通知
     *
     * @param alert 报警DTO
     */
    @Async
    public void processAlertNotification(AlertDTO alert) {
        try {
            String channels = alert.getNotificationChannels();
            if (channels == null) return;

            // 遍历所有通知渠道
            for (String channel : channels.split(",")) {
                switch (AlertRule.NotificationChannel.valueOf(channel.trim())) {
                    case SMS -> sendSmsNotification(alert);
                    case EMAIL -> sendEmailNotification(alert);
                    case VOICE -> sendVoiceNotification(alert);
                    case WEBHOOK -> sendWebhookNotification(alert);
                }
            }

            // 标记该报警已通知，缓存24小时
            String cacheKey = "alert:notified:" + alert.getAlertNo();
            redisTemplate.opsForValue().set(cacheKey, "1", 24, TimeUnit.HOURS);

            log.info("报警通知已处理完成 - 报警编号: {}", alert.getAlertNo());
        } catch (Exception e) {
            log.error("处理报警通知失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送短信通知
     * 通过HTTP调用第三方短信API发送报警短信
     *
     * @param alert 报警信息
     */
    private void sendSmsNotification(AlertDTO alert) {
        if (!smsEnabled || smsApiUrl.isEmpty() || smsPhoneNumbers.isEmpty()) {
            log.warn("短信通知未配置或未启用 - 报警编号: {}", alert.getAlertNo());
            return;
        }

        try {
            // 构建短信内容
            String content = String.format("【煤矿安全报警】%s-%s 数值:%s%s 超过阈值:%s%s，请及时处理！",
                    alert.getSensorName(), alert.getSensorType(),
                    alert.getAlertValue(), getUnitByType(alert.getSensorType()),
                    alert.getThresholdValue(), getUnitByType(alert.getSensorType()));

            // 遍历所有接收人手机号
            for (String phone : smsPhoneNumbers.split(",")) {
                // 构建请求体（根据实际短信API格式调整）
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("apiKey", smsApiKey);
                requestBody.put("templateId", smsTemplateId);
                requestBody.put("mobile", phone.trim());
                requestBody.put("params", List.of(
                        alert.getSensorName(),
                        alert.getAlertValue(),
                        alert.getLevel(),
                        alert.getLocation()
                ));

                // 发送HTTP请求
                sendHttpRequest(smsApiUrl, requestBody);
                log.info("短信通知已发送 - 手机号: {}, 报警编号: {}", phone, alert.getAlertNo());
            }
        } catch (Exception e) {
            log.error("发送短信通知失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送邮件通知
     * 通过SMTP服务器发送报警邮件
     *
     * @param alert 报警信息
     */
    private void sendEmailNotification(AlertDTO alert) {
        if (!emailEnabled || emailTo.isEmpty()) {
            log.warn("邮件通知未配置或未启用 - 报警编号: {}", alert.getAlertNo());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailFrom);
            message.setTo(emailTo.split(","));
            message.setSubject(String.format("【%s】煤矿安全报警 - %s",
                    alert.getLevel(), alert.getRuleName()));

            // 构建邮件正文
            StringBuilder body = new StringBuilder();
            body.append("煤矿安全生产报警通知\n");
            body.append("========================\n");
            body.append("报警编号: ").append(alert.getAlertNo()).append("\n");
            body.append("报警级别: ").append(alert.getLevel()).append("\n");
            body.append("报警时间: ").append(alert.getFirstAlertTime()).append("\n");
            body.append("传感器: ").append(alert.getSensorName()).append("(").append(alert.getSensorId()).append(")\n");
            body.append("传感器类型: ").append(alert.getSensorType()).append("\n");
            body.append("安装位置: ").append(alert.getLocation()).append("\n");
            body.append("当前数值: ").append(alert.getAlertValue()).append(getUnitByType(alert.getSensorType())).append("\n");
            body.append("报警阈值: ").append(alert.getThresholdValue()).append(getUnitByType(alert.getSensorType())).append("\n");
            body.append("报警规则: ").append(alert.getRuleName()).append("\n");
            body.append("报警描述: ").append(alert.getDescription()).append("\n");
            body.append("========================\n");
            body.append("请立即采取相应措施！");

            message.setText(body.toString());

            mailSender.send(message);
            log.info("邮件通知已发送 - 收件人: {}, 报警编号: {}", emailTo, alert.getAlertNo());
        } catch (Exception e) {
            log.error("发送邮件通知失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送语音通知
     * 通过HTTP调用第三方语音通知API
     *
     * @param alert 报警信息
     */
    private void sendVoiceNotification(AlertDTO alert) {
        if (!voiceEnabled || voiceApiUrl.isEmpty() || voicePhoneNumbers.isEmpty()) {
            log.warn("语音通知未配置或未启用 - 报警编号: {}", alert.getAlertNo());
            return;
        }

        try {
            // 构建语音通知内容
            String content = String.format("煤矿安全报警，%s，数值%s，超过阈值，请立即处理。",
                    alert.getSensorName(), alert.getAlertValue());

            // 遍历所有接收人手机号
            for (String phone : voicePhoneNumbers.split(",")) {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("calledNumber", phone.trim());
                requestBody.put("ttsCode", "ALERT_TEMPLATE");
                requestBody.put("ttsParam", JSON.toJSONString(Map.of(
                        "sensorName", alert.getSensorName(),
                        "value", alert.getAlertValue().toString(),
                        "level", alert.getLevel()
                )));

                sendHttpRequest(voiceApiUrl, requestBody);
                log.info("语音通知已发送 - 手机号: {}, 报警编号: {}", phone, alert.getAlertNo());
            }
        } catch (Exception e) {
            log.error("发送语音通知失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送Webhook通知
     * 向配置的Webhook地址发送HTTP POST请求
     *
     * @param alert 报警信息
     */
    private void sendWebhookNotification(AlertDTO alert) {
        if (!webhookEnabled || webhookUrls == null || webhookUrls.isEmpty()) {
            log.warn("Webhook通知未配置或未启用 - 报警编号: {}", alert.getAlertNo());
            return;
        }

        try {
            // 构建Webhook请求体
            Map<String, Object> webhookData = new HashMap<>();
            webhookData.put("alertNo", alert.getAlertNo());
            webhookData.put("level", alert.getLevel());
            webhookData.put("sensorId", alert.getSensorId());
            webhookData.put("sensorName", alert.getSensorName());
            webhookData.put("sensorType", alert.getSensorType());
            webhookData.put("location", alert.getLocation());
            webhookData.put("alertValue", alert.getAlertValue());
            webhookData.put("thresholdValue", alert.getThresholdValue());
            webhookData.put("unit", getUnitByType(alert.getSensorType()));
            webhookData.put("ruleName", alert.getRuleName());
            webhookData.put("description", alert.getDescription());
            webhookData.put("firstAlertTime", alert.getFirstAlertTime());
            webhookData.put("timestamp", LocalDateTime.now().toString());

            // 向所有配置的Webhook地址发送通知
            for (String url : webhookUrls) {
                if (url != null && !url.trim().isEmpty()) {
                    sendHttpRequest(url.trim(), webhookData);
                    log.info("Webhook通知已发送 - URL: {}, 报警编号: {}", url, alert.getAlertNo());
                }
            }
        } catch (Exception e) {
            log.error("发送Webhook通知失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送HTTP POST请求（通用工具方法）
     *
     * @param url         请求地址
     * @param requestBody 请求体（Map格式，自动转为JSON）
     */
    private void sendHttpRequest(String url, Map<String, Object> requestBody) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json;charset=UTF-8");

            String jsonBody = JSON.toJSONString(requestBody);
            httpPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

            client.execute(httpPost, response -> {
                int statusCode = response.getCode();
                if (statusCode >= 200 && statusCode < 300) {
                    log.debug("HTTP请求成功 - URL: {}, 状态码: {}", url, statusCode);
                } else {
                    log.warn("HTTP请求返回非成功状态 - URL: {}, 状态码: {}", url, statusCode);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("HTTP请求失败 - URL: {}, 错误: {}", url, e.getMessage());
        }
    }

    /**
     * 根据传感器类型获取单位
     *
     * @param sensorType 传感器类型
     * @return 单位字符串
     */
    private String getUnitByType(String sensorType) {
        return switch (sensorType) {
            case "GAS" -> "% CH4";
            case "DUST" -> "mg/m³";
            case "CO" -> "ppm";
            case "TEMPERATURE" -> "℃";
            case "WIND" -> "m/s";
            default -> "";
        };
    }

    /**
     * 确认报警（处理报警）
     *
     * @param alertNo  报警编号
     * @param status   新状态
     * @param operator 操作人
     * @param comment  处理备注
     * @return 更新后的报警实体
     */
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

    /**
     * 生成唯一报警编号
     * 格式：ALT + yyyyMMddHHmmss + 8位随机UUID
     *
     * @return 报警编号
     */
    private String generateAlertNo() {
        return "ALT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * 实体转DTO
     *
     * @param alert 报警实体
     * @return 报警DTO
     */
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

    /**
     * 获取报警统计信息
     *
     * @return 统计数据Map
     */
    public Map<String, Object> getAlertStatistics() {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        stats.put("pendingCount", alertRepository.countByStatus(Alert.AlertStatus.PENDING.getValue()));
        stats.put("processingCount", alertRepository.countByStatus(Alert.AlertStatus.PROCESSING.getValue()));
        stats.put("emergencyToday", alertRepository.countByLevelAndTimeAfter("EMERGENCY", today));
        stats.put("alertToday", alertRepository.countByLevelAndTimeAfter("ALERT", today));
        stats.put("warningToday", alertRepository.countByLevelAndTimeAfter("WARNING", today));

        return stats;
    }
}
