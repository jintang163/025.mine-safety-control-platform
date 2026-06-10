package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mine.safety.domain.Sensor;
import com.mine.safety.domain.TrendAlert;
import com.mine.safety.domain.TrendRule;
import com.mine.safety.dto.TrendAnalysisDTO;
import com.mine.safety.dto.TrendRuleDTO;
import com.mine.safety.repository.TrendAlertRepository;
import com.mine.safety.repository.TrendRuleRepository;
import com.mine.safety.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrendAnalysisService {

    private final TrendRuleRepository trendRuleRepository;
    private final TrendAlertRepository trendAlertRepository;
    private final SensorRepository sensorRepository;
    private final HistoryAnalysisService historyAnalysisService;
    private final MessagePushService messagePushService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final AtomicInteger ALERT_SEQ = new AtomicInteger(0);

    public void executeTrendCheck() {
        List<TrendRule> rules = trendRuleRepository.selectList(
                new LambdaQueryWrapper<TrendRule>().eq(TrendRule::getEnabled, true));

        for (TrendRule rule : rules) {
            try {
                checkTrendRule(rule);
            } catch (Exception e) {
                log.error("趋势规则检测失败 - 规则: {}, 错误: {}", rule.getRuleCode(), e.getMessage());
            }
        }
    }

    private void checkTrendRule(TrendRule rule) {
        int periodDays = switch (rule.getPeriodUnit()) {
            case "DAY" -> 1;
            case "WEEK" -> 7;
            case "MONTH" -> 30;
            default -> 7;
        };

        int totalDays = periodDays * rule.getConsecutivePeriods() + periodDays;
        String startDate = LocalDate.now().minusDays(totalDays).format(DATE_FMT);

        List<String> zoneCodes = getDistinctZoneCodes(rule.getSensorType());
        if (zoneCodes.isEmpty()) {
            zoneCodes.add(null);
        }

        for (String zoneCode : zoneCodes) {
            List<BigDecimal> periodValues = calculateMetricPeriods(rule, zoneCode, startDate, periodDays);

            if (periodValues.size() < rule.getConsecutivePeriods()) {
                continue;
            }

            boolean trendDetected = detectTrend(periodValues, rule.getTrendDirection(), rule.getConsecutivePeriods());

            if (trendDetected) {
                boolean alreadyAlerted = checkExistingAlert(rule, zoneCode);
                if (!alreadyAlerted) {
                    createTrendAlert(rule, zoneCode, periodValues, periodDays);
                }
            }
        }
    }

    private List<BigDecimal> calculateMetricPeriods(TrendRule rule, String zoneCode, String startDate, int periodDays) {
        return switch (rule.getMetric()) {
            case "DAILY_AVG" -> {
                int totalDays = periodDays * rule.getConsecutivePeriods();
                List<BigDecimal> dailyAvgs = historyAnalysisService.getDailyAvgValues(
                        rule.getSensorType(), zoneCode, startDate, totalDays);
                yield aggregateIntoPeriods(dailyAvgs, periodDays);
            }
            case "DAILY_MAX" -> {
                int totalDays = periodDays * rule.getConsecutivePeriods();
                List<BigDecimal> dailyAvgs = historyAnalysisService.getDailyAvgValues(
                        rule.getSensorType(), zoneCode, startDate, totalDays);
                yield aggregateIntoPeriods(dailyAvgs, periodDays);
            }
            case "OVER_THRESHOLD_COUNT" -> {
                int totalDays = periodDays * rule.getConsecutivePeriods();
                BigDecimal threshold = rule.getThresholdValue() != null ? rule.getThresholdValue() : BigDecimal.ONE;
                List<BigDecimal> dailyCounts = historyAnalysisService.getOverThresholdDailyCounts(
                        rule.getSensorType(), zoneCode, threshold, startDate, totalDays);
                yield aggregateIntoPeriods(dailyCounts, periodDays);
            }
            default -> new ArrayList<>();
        };
    }

    private List<BigDecimal> aggregateIntoPeriods(List<BigDecimal> dailyValues, int periodDays) {
        List<BigDecimal> periodValues = new ArrayList<>();
        for (int i = 0; i + periodDays <= dailyValues.size(); i += periodDays) {
            BigDecimal sum = BigDecimal.ZERO;
            int count = 0;
            for (int j = i; j < i + periodDays && j < dailyValues.size(); j++) {
                sum = sum.add(dailyValues.get(j));
                count++;
            }
            if (count > 0) {
                periodValues.add(sum.divide(BigDecimal.valueOf(count), 4, BigDecimal.ROUND_HALF_UP));
            }
        }
        return periodValues;
    }

    private boolean detectTrend(List<BigDecimal> values, String direction, int consecutivePeriods) {
        if (values.size() < consecutivePeriods) {
            return false;
        }

        int recentStart = values.size() - consecutivePeriods;
        for (int i = recentStart + 1; i < values.size(); i++) {
            if ("RISING".equals(direction)) {
                if (values.get(i).compareTo(values.get(i - 1)) <= 0) {
                    return false;
                }
            } else if ("FALLING".equals(direction)) {
                if (values.get(i).compareTo(values.get(i - 1)) >= 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkExistingAlert(TrendRule rule, String zoneCode) {
        LambdaQueryWrapper<TrendAlert> wrapper = new LambdaQueryWrapper<TrendAlert>()
                .eq(TrendAlert::getRuleId, rule.getId())
                .eq(TrendAlert::getStatus, 0);

        if (zoneCode != null) {
            wrapper.eq(TrendAlert::getZoneCode, zoneCode);
        } else {
            wrapper.isNull(TrendAlert::getZoneCode);
        }

        return trendAlertRepository.selectCount(wrapper) > 0;
    }

    @Transactional
    public void createTrendAlert(TrendRule rule, String zoneCode, List<BigDecimal> periodValues, int periodDays) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays((long) periodDays * rule.getConsecutivePeriods());

        TrendAlert alert = new TrendAlert();
        alert.setAlertNo(generateAlertNo());
        alert.setRuleId(rule.getId());
        alert.setRuleCode(rule.getRuleCode());
        alert.setRuleName(rule.getRuleName());
        alert.setSensorType(rule.getSensorType());
        alert.setZoneCode(zoneCode);
        alert.setMetric(rule.getMetric());
        alert.setTrendDirection(rule.getTrendDirection());
        alert.setConsecutivePeriods(rule.getConsecutivePeriods());
        alert.setPeriodUnit(rule.getPeriodUnit());
        alert.setStartDate(startDate);
        alert.setEndDate(endDate);

        List<TrendAnalysisDTO.PeriodValue> pvList = new ArrayList<>();
        for (int i = 0; i < periodValues.size(); i++) {
            TrendAnalysisDTO.PeriodValue pv = new TrendAnalysisDTO.PeriodValue();
            pv.setPeriod(startDate.plusDays((long) i * periodDays).format(DATE_FMT));
            pv.setValue(periodValues.get(i));
            pvList.add(pv);
        }
        alert.setTrendData(JSON.toJSONString(pvList));

        String directionText = "RISING".equals(rule.getTrendDirection()) ? "上升" : "下降";
        String periodText = switch (rule.getPeriodUnit()) {
            case "DAY" -> "日";
            case "WEEK" -> "周";
            case "MONTH" -> "月";
            default -> rule.getPeriodUnit();
        };
        String zoneText = zoneCode != null ? zoneCode : "全局";
        alert.setDescription(String.format("区域[%s] %s传感器%s指标连续%d%s持续%s，请及时评估风险",
                zoneText, rule.getSensorType(), rule.getMetric(),
                rule.getConsecutivePeriods(), periodText, directionText));
        alert.setSeverity(rule.getSeverity());
        alert.setStatus(0);

        trendAlertRepository.insert(alert);
        log.warn("趋势预警已创建 - 编号: {}, 规则: {}, 区域: {}", alert.getAlertNo(), rule.getRuleCode(), zoneCode);

        if (rule.getNotificationChannels() != null) {
            try {
                messagePushService.pushTrendAlertMessage(alert, rule.getNotificationChannels());
            } catch (Exception e) {
                log.error("趋势预警通知推送失败: {}", e.getMessage());
            }
        }
    }

    private List<String> getDistinctZoneCodes(String sensorType) {
        LambdaQueryWrapper<Sensor> wrapper = new LambdaQueryWrapper<>();
        if (sensorType != null) {
            wrapper.eq(Sensor::getType, sensorType);
        }
        wrapper.select(Sensor::getZoneCode).isNotNull(Sensor::getZoneCode()).groupBy(Sensor::getZoneCode);

        return sensorRepository.selectList(wrapper).stream()
                .map(Sensor::getZoneCode)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<TrendAnalysisDTO> getTrendAlerts(String sensorType, String zoneCode, Integer status) {
        LambdaQueryWrapper<TrendAlert> wrapper = new LambdaQueryWrapper<>();
        if (sensorType != null) wrapper.eq(TrendAlert::getSensorType, sensorType);
        if (zoneCode != null) wrapper.eq(TrendAlert::getZoneCode, zoneCode);
        if (status != null) wrapper.eq(TrendAlert::getStatus, status);
        wrapper.orderByDesc(TrendAlert::getCreatedAt);

        return trendAlertRepository.selectList(wrapper).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TrendAlert acknowledgeTrendAlert(String alertNo, String acknowledgedBy) {
        TrendAlert alert = trendAlertRepository.selectOne(
                new LambdaQueryWrapper<TrendAlert>().eq(TrendAlert::getAlertNo, alertNo));
        if (alert == null) throw new RuntimeException("趋势预警不存在: " + alertNo);

        alert.setStatus(1);
        alert.setAcknowledgedBy(acknowledgedBy);
        alert.setAcknowledgedAt(LocalDateTime.now());
        trendAlertRepository.updateById(alert);
        return alert;
    }

    public List<TrendRuleDTO> getTrendRules() {
        return trendRuleRepository.selectList(
                new LambdaQueryWrapper<TrendRule>().orderByAsc(TrendRule::getSensorType))
                .stream().map(this::convertRuleToDTO).collect(Collectors.toList());
    }

    @Transactional
    public TrendRuleDTO saveTrendRule(TrendRuleDTO dto) {
        TrendRule rule = new TrendRule();
        rule.setRuleCode(dto.getRuleCode());
        rule.setRuleName(dto.getRuleName());
        rule.setDescription(dto.getDescription());
        rule.setSensorType(dto.getSensorType());
        rule.setZoneCode(dto.getZoneCode());
        rule.setMetric(dto.getMetric());
        rule.setTrendDirection(dto.getTrendDirection());
        rule.setConsecutivePeriods(dto.getConsecutivePeriods());
        rule.setPeriodUnit(dto.getPeriodUnit());
        rule.setThresholdValue(dto.getThresholdValue());
        rule.setSeverity(dto.getSeverity());
        rule.setNotificationChannels(dto.getNotificationChannels());
        rule.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);

        if (dto.getId() != null) {
            rule.setId(dto.getId());
            trendRuleRepository.updateById(rule);
        } else {
            trendRuleRepository.insert(rule);
        }
        return convertRuleToDTO(rule);
    }

    private String generateAlertNo() {
        return "TA-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + String.format("%04d", ALERT_SEQ.incrementAndGet() % 10000);
    }

    private TrendAnalysisDTO convertToDTO(TrendAlert alert) {
        TrendAnalysisDTO dto = new TrendAnalysisDTO();
        dto.setAlertNo(alert.getAlertNo());
        dto.setRuleCode(alert.getRuleCode());
        dto.setRuleName(alert.getRuleName());
        dto.setSensorType(alert.getSensorType());
        dto.setZoneCode(alert.getZoneCode());
        dto.setMetric(alert.getMetric());
        dto.setTrendDirection(alert.getTrendDirection());
        dto.setConsecutivePeriods(alert.getConsecutivePeriods());
        dto.setPeriodUnit(alert.getPeriodUnit());
        dto.setStartDate(alert.getStartDate() != null ? alert.getStartDate().toString() : null);
        dto.setEndDate(alert.getEndDate() != null ? alert.getEndDate().toString() : null);
        dto.setDescription(alert.getDescription());
        dto.setSeverity(alert.getSeverity());

        if (alert.getTrendData() != null) {
            dto.setPeriodValues(JSON.parseArray(alert.getTrendData(), TrendAnalysisDTO.PeriodValue.class));
        }
        return dto;
    }

    private TrendRuleDTO convertRuleToDTO(TrendRule rule) {
        TrendRuleDTO dto = new TrendRuleDTO();
        dto.setId(rule.getId());
        dto.setRuleCode(rule.getRuleCode());
        dto.setRuleName(rule.getRuleName());
        dto.setDescription(rule.getDescription());
        dto.setSensorType(rule.getSensorType());
        dto.setZoneCode(rule.getZoneCode());
        dto.setMetric(rule.getMetric());
        dto.setTrendDirection(rule.getTrendDirection());
        dto.setConsecutivePeriods(rule.getConsecutivePeriods());
        dto.setPeriodUnit(rule.getPeriodUnit());
        dto.setThresholdValue(rule.getThresholdValue());
        dto.setSeverity(rule.getSeverity());
        dto.setNotificationChannels(rule.getNotificationChannels());
        dto.setEnabled(rule.getEnabled());
        return dto;
    }
}
