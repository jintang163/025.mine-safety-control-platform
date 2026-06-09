package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mine.safety.domain.AlertRuleDefinition;
import com.mine.safety.domain.AlertRuleDefinition.RuleType;
import com.mine.safety.domain.LinkageAction;
import com.mine.safety.drools.RuleSensorData;
import com.mine.safety.dto.AlertDTO;
import com.mine.safety.dto.SensorDataDTO;
import com.mine.safety.dto.ThresholdDTO;
import com.mine.safety.repository.AlertRuleDefinitionRepository;
import com.mine.safety.repository.LinkageActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private final KieContainer kieContainer;
    private final AlertRuleDefinitionRepository ruleRepository;
    private final LinkageActionRepository actionRepository;
    private final LinkageActionEngineService actionEngineService;
    private final ThresholdCacheService thresholdCacheService;
    private final StringRedisTemplate redisTemplate;
    private final WebSocketPushService webSocketPushService;

    private static final String ALERT_COOLDOWN_KEY = "rule:alert:cooldown:";
    private static final String SENSOR_HISTORY_KEY = "rule:sensor:history:";
    private static final int MAX_HISTORY_SIZE = 100;
    private static final int DEFAULT_COOLDOWN_SECONDS = 30;

    private final Map<String, List<SensorDataDTO>> sensorHistoryCache = new ConcurrentHashMap<>();

    public List<AlertDTO> executeRules(SensorDataDTO sensorData) {
        List<AlertDTO> alerts = new ArrayList<>();

        try {
            List<SensorDataDTO> history = getSensorHistory(sensorData.getSensorId());
            history.add(sensorData);
            if (history.size() > MAX_HISTORY_SIZE) {
                history.remove(0);
            }
            saveSensorHistory(sensorData.getSensorId(), history);

            RuleSensorData ruleData = buildRuleSensorData(sensorData, history);

            List<RuleSensorData> triggeredResults = executeDroolsRules(ruleData);

            for (RuleSensorData result : triggeredResults) {
                if (checkCooldown(result.getSensorId(), result.getAlertRuleCode())) {
                    continue;
                }

                AlertDTO alert = createAlertFromRuleResult(result);
                alerts.add(alert);

                setCooldown(result.getSensorId(), result.getAlertRuleCode());

                triggerLinkageActions(result, alert);

                webSocketPushService.pushAlert(alert);
            }

        } catch (Exception e) {
            log.error("规则引擎执行异常 - 传感器: {}", sensorData.getSensorId(), e);
        }

        return alerts;
    }

    private RuleSensorData buildRuleSensorData(SensorDataDTO sensorData, List<SensorDataDTO> history) {
        ThresholdDTO threshold = thresholdCacheService.getThreshold(sensorData.getSensorId());

        RuleSensorData.RuleSensorDataBuilder builder = RuleSensorData.builder()
                .sensorId(sensorData.getSensorId())
                .sensorType(sensorData.getSensorType())
                .zoneCode(sensorData.getZoneCode())
                .value(sensorData.getValue())
                .unit(sensorData.getUnit())
                .timestamp(sensorData.getTimestamp())
                .historyData(history)
                .alertTriggered(false)
                .enabled(true);

        if (threshold != null) {
            builder.warningThreshold(threshold.getWarningThreshold())
                    .alarmThreshold(threshold.getAlarmThreshold())
                    .powerOffThreshold(threshold.getPowerOffThreshold());
        }

        return builder.build();
    }

    private List<RuleSensorData> executeDroolsRules(RuleSensorData ruleData) {
        List<RuleSensorData> results = new ArrayList<>();

        KieSession kieSession = kieContainer.newKieSession();
        try {
            kieSession.setGlobal("alertResults", results);
            kieSession.insert(ruleData);

            List<AlertRuleDefinition> dynamicRules = loadDynamicRules(ruleData);
            for (AlertRuleDefinition rule : dynamicRules) {
                if (rule.getDroolsRule() != null && !rule.getDroolsRule().isEmpty()) {
                    kieSession.insert(rule);
                }
            }

            int firedRules = kieSession.fireAllRules();
            log.debug("Drools规则执行完成 - 传感器: {}, 触发规则数: {}", ruleData.getSensorId(), firedRules);

        } finally {
            kieSession.dispose();
        }

        return results;
    }

    private List<AlertRuleDefinition> loadDynamicRules(RuleSensorData ruleData) {
        return ruleRepository.findMatchingRules(
                ruleData.getSensorType(),
                ruleData.getSensorId(),
                ruleData.getZoneCode()
        );
    }

    private boolean checkCooldown(String sensorId, String ruleCode) {
        String key = ALERT_COOLDOWN_KEY + sensorId + ":" + ruleCode;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    private void setCooldown(String sensorId, String ruleCode) {
        String key = ALERT_COOLDOWN_KEY + sensorId + ":" + ruleCode;
        redisTemplate.opsForValue().set(key, "1", DEFAULT_COOLDOWN_SECONDS, TimeUnit.SECONDS);
    }

    private List<SensorDataDTO> getSensorHistory(String sensorId) {
        String key = SENSOR_HISTORY_KEY + sensorId;
        String historyJson = redisTemplate.opsForValue().get(key);
        if (historyJson != null && !historyJson.isEmpty()) {
            try {
                return JSON.parseArray(historyJson, SensorDataDTO.class);
            } catch (Exception e) {
                log.warn("解析传感器历史数据失败 - 传感器: {}", sensorId);
            }
        }
        return sensorHistoryCache.getOrDefault(sensorId, new ArrayList<>());
    }

    private void saveSensorHistory(String sensorId, List<SensorDataDTO> history) {
        sensorHistoryCache.put(sensorId, history);
        String key = SENSOR_HISTORY_KEY + sensorId;
        redisTemplate.opsForValue().set(key, JSON.toJSONString(history), 1, TimeUnit.HOURS);
    }

    private AlertDTO createAlertFromRuleResult(RuleSensorData result) {
        String alertNo = "ALERT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + String.format("%04d", (int) (Math.random() * 10000));

        return AlertDTO.builder()
                .alertNo(alertNo)
                .sensorId(result.getSensorId())
                .sensorType(result.getSensorType())
                .alertValue(result.getValue())
                .thresholdValue(getThresholdByLevel(result))
                .level(result.getAlertLevel())
                .ruleId(0L)
                .ruleCode(result.getAlertRuleCode())
                .ruleName(getRuleNameByCode(result.getAlertRuleCode()))
                .description(result.getAlertDescription())
                .zoneCode(result.getZoneCode())
                .status(0)
                .firstAlertTime(LocalDateTime.now())
                .lastAlertTime(LocalDateTime.now())
                .alertCount(1)
                .build();
    }

    private java.math.BigDecimal getThresholdByLevel(RuleSensorData data) {
        return switch (data.getAlertLevel()) {
            case "WARNING" -> data.getWarningThreshold();
            case "ALERT" -> data.getAlarmThreshold();
            case "EMERGENCY" -> data.getPowerOffThreshold();
            default -> data.getWarningThreshold();
        };
    }

    private String getRuleNameByCode(String ruleCode) {
        AlertRuleDefinition rule = ruleRepository.selectOne(
                new LambdaQueryWrapper<AlertRuleDefinition>().eq(AlertRuleDefinition::getRuleCode, ruleCode));
        return rule != null ? rule.getRuleName() : ruleCode;
    }

    private void triggerLinkageActions(RuleSensorData ruleData, AlertDTO alert) {
        try {
            String ruleCode = ruleData.getAlertRuleCode();
            AlertRuleDefinition ruleDef = ruleRepository.selectOne(
                    new LambdaQueryWrapper<AlertRuleDefinition>().eq(AlertRuleDefinition::getRuleCode, ruleCode));
            Long ruleId = ruleDef != null ? ruleDef.getId() : null;

            if (ruleId == null) {
                return;
            }

            List<LinkageAction> actions = actionRepository.findActionsByRuleId(ruleId);

            actionEngineService.executeActions(actions, ruleData, alert, ruleId, ruleCode);

        } catch (Exception e) {
            log.error("触发联动动作异常 - 规则: {}", ruleData.getAlertRuleCode(), e);
        }
    }

    public void reloadRules() {
        log.info("重新加载所有规则...");
        kieContainer.updateToVersion(kieContainer.getReleaseId());
        sensorHistoryCache.clear();
        log.info("所有规则重新加载完成");
    }

    public List<AlertRuleDefinition> getAllRules() {
        return ruleRepository.selectList(null);
    }

    public List<AlertRuleDefinition> getEnabledRules() {
        return ruleRepository.selectList(new LambdaQueryWrapper<AlertRuleDefinition>().eq(AlertRuleDefinition::getEnabled, true));
    }

    public List<AlertRuleDefinition> getRulesByType(RuleType ruleType) {
        return ruleRepository.selectList(new LambdaQueryWrapper<AlertRuleDefinition>()
                .eq(AlertRuleDefinition::getRuleType, ruleType.name())
                .eq(AlertRuleDefinition::getEnabled, true));
    }
}
