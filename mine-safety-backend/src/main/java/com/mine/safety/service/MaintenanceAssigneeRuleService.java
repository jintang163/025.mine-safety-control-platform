package com.mine.safety.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mine.safety.domain.MaintenanceAssigneeRule;
import com.mine.safety.repository.MaintenanceAssigneeRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceAssigneeRuleService {

    private final MaintenanceAssigneeRuleRepository ruleRepository;

    public MaintenanceAssigneeRule findAssignee(String zoneCode, String sensorType, String faultType) {
        List<MaintenanceAssigneeRule> rules = ruleRepository.selectList(
                new LambdaQueryWrapper<MaintenanceAssigneeRule>()
                        .eq(MaintenanceAssigneeRule::getEnabled, true)
                        .and(wrapper -> wrapper
                                .and(w -> w.eq(MaintenanceAssigneeRule::getZoneCode, zoneCode)
                                        .eq(MaintenanceAssigneeRule::getSensorType, sensorType)
                                        .eq(MaintenanceAssigneeRule::getFaultType, faultType))
                                .or(w -> w.eq(MaintenanceAssigneeRule::getZoneCode, zoneCode)
                                        .eq(MaintenanceAssigneeRule::getSensorType, sensorType)
                                        .isNull(MaintenanceAssigneeRule::getFaultType))
                                .or(w -> w.eq(MaintenanceAssigneeRule::getZoneCode, zoneCode)
                                        .isNull(MaintenanceAssigneeRule::getSensorType)
                                        .isNull(MaintenanceAssigneeRule::getFaultType))
                                .or(w -> w.isNull(MaintenanceAssigneeRule::getZoneCode)
                                        .eq(MaintenanceAssigneeRule::getSensorType, sensorType)
                                        .isNull(MaintenanceAssigneeRule::getFaultType))
                                .or(w -> w.isNull(MaintenanceAssigneeRule::getZoneCode)
                                        .isNull(MaintenanceAssigneeRule::getSensorType)
                                        .isNull(MaintenanceAssigneeRule::getFaultType))
                        ));

        return rules.stream()
                .min(Comparator.comparingInt(this::matchScore).reversed())
                .orElse(null);
    }

    private int matchScore(MaintenanceAssigneeRule rule) {
        int score = 0;
        if (rule.getZoneCode() != null) score += 4;
        if (rule.getSensorType() != null) score += 2;
        if (rule.getFaultType() != null) score += 1;
        return score;
    }

    public List<MaintenanceAssigneeRule> getAllRules() {
        return ruleRepository.selectList(
                new LambdaQueryWrapper<MaintenanceAssigneeRule>()
                        .orderByAsc(MaintenanceAssigneeRule::getZoneCode)
                        .orderByAsc(MaintenanceAssigneeRule::getSensorType)
                        .orderByAsc(MaintenanceAssigneeRule::getFaultType));
    }

    public MaintenanceAssigneeRule saveRule(MaintenanceAssigneeRule rule) {
        if (rule.getId() == null) {
            ruleRepository.insert(rule);
        } else {
            ruleRepository.updateById(rule);
        }
        return rule;
    }

    public void deleteRule(Long id) {
        ruleRepository.deleteById(id);
    }
}
