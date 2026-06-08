package com.mine.safety.controller;

import com.mine.safety.domain.AlertRuleDefinition;
import com.mine.safety.dto.AlertRuleDTO;
import com.mine.safety.dto.ResponseDTO;
import com.mine.safety.repository.AlertRuleDefinitionRepository;
import com.mine.safety.repository.RuleActionRelationRepository;
import com.mine.safety.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleDefinitionRepository ruleRepository;
    private final RuleActionRelationRepository relationRepository;
    private final RuleEngineService ruleEngineService;

    @GetMapping
    public ResponseDTO<Page<AlertRuleDTO>> getRules(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<AlertRuleDefinition> page = ruleRepository.findAll(pageable);
        return ResponseDTO.success(page.map(AlertRuleDTO::fromEntity));
    }

    @GetMapping("/enabled")
    public ResponseDTO<List<AlertRuleDTO>> getEnabledRules() {
        List<AlertRuleDefinition> rules = ruleEngineService.getEnabledRules();
        return ResponseDTO.success(rules.stream().map(AlertRuleDTO::fromEntity).toList());
    }

    @GetMapping("/type/{ruleType}")
    public ResponseDTO<List<AlertRuleDTO>> getRulesByType(@PathVariable String ruleType) {
        List<AlertRuleDefinition> rules = ruleRepository.findByRuleTypeAndEnabled(ruleType, true);
        return ResponseDTO.success(rules.stream().map(AlertRuleDTO::fromEntity).toList());
    }

    @GetMapping("/{id}")
    public ResponseDTO<AlertRuleDTO> getRuleById(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(rule -> ResponseDTO.success(AlertRuleDTO.fromEntity(rule)))
                .orElse(ResponseDTO.error("规则不存在"));
    }

    @GetMapping("/code/{ruleCode}")
    public ResponseDTO<AlertRuleDTO> getRuleByCode(@PathVariable String ruleCode) {
        return ruleRepository.findByRuleCode(ruleCode)
                .map(rule -> ResponseDTO.success(AlertRuleDTO.fromEntity(rule)))
                .orElse(ResponseDTO.error("规则不存在"));
    }

    @PostMapping
    public ResponseDTO<AlertRuleDTO> createRule(@RequestBody AlertRuleDTO dto) {
        if (ruleRepository.existsByRuleCode(dto.getRuleCode())) {
            return ResponseDTO.error("规则编码已存在");
        }

        AlertRuleDefinition rule = dto.toEntity();
        rule.setVersion(1);
        rule = ruleRepository.save(rule);

        log.info("创建规则成功 - 规则: {}, 编码: {}", rule.getRuleName(), rule.getRuleCode());
        return ResponseDTO.success(AlertRuleDTO.fromEntity(rule));
    }

    @PutMapping("/{id}")
    public ResponseDTO<AlertRuleDTO> updateRule(@PathVariable Long id, @RequestBody AlertRuleDTO dto) {
        return ruleRepository.findById(id)
                .map(rule -> {
                    rule.setRuleName(dto.getRuleName());
                    rule.setRuleType(dto.getRuleType());
                    rule.setSensorType(dto.getSensorType());
                    rule.setSensorId(dto.getSensorId());
                    rule.setZoneCode(dto.getZoneCode());
                    rule.setDroolsRule(dto.getDroolsRule());
                    rule.setGroovyScript(dto.getGroovyScript());
                    rule.setRuleParams(dto.getRuleParams());
                    rule.setLevel(dto.getLevel());
                    rule.setDescription(dto.getDescription());
                    rule.setEnabled(dto.getEnabled());
                    rule.setVersion(rule.getVersion() + 1);
                    rule.setUpdatedBy(dto.getUpdatedBy());

                    AlertRuleDefinition saved = ruleRepository.save(rule);
                    log.info("更新规则成功 - 规则: {}", saved.getRuleCode());
                    return ResponseDTO.success(AlertRuleDTO.fromEntity(saved));
                })
                .orElse(ResponseDTO.error("规则不存在"));
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> deleteRule(@PathVariable Long id) {
        if (!ruleRepository.existsById(id)) {
            return ResponseDTO.error("规则不存在");
        }

        relationRepository.deleteByRuleId(id);
        ruleRepository.deleteById(id);
        log.info("删除规则成功 - ID: {}", id);
        return ResponseDTO.success();
    }

    @PutMapping("/{id}/enable")
    public ResponseDTO<AlertRuleDTO> enableRule(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(rule -> {
                    rule.setEnabled(true);
                    AlertRuleDefinition saved = ruleRepository.save(rule);
                    log.info("启用规则 - 编码: {}", saved.getRuleCode());
                    return ResponseDTO.success(AlertRuleDTO.fromEntity(saved));
                })
                .orElse(ResponseDTO.error("规则不存在"));
    }

    @PutMapping("/{id}/disable")
    public ResponseDTO<AlertRuleDTO> disableRule(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(rule -> {
                    rule.setEnabled(false);
                    AlertRuleDefinition saved = ruleRepository.save(rule);
                    log.info("禁用规则 - 编码: {}", saved.getRuleCode());
                    return ResponseDTO.success(AlertRuleDTO.fromEntity(saved));
                })
                .orElse(ResponseDTO.error("规则不存在"));
    }

    @PostMapping("/reload")
    public ResponseDTO<Map<String, Object>> reloadRules() {
        ruleEngineService.reloadRules();
        int count = ruleEngineService.getEnabledRules().size();
        return ResponseDTO.success(Map.of(
                "message", "规则重新加载成功",
                "enabledRulesCount", count
        ));
    }

    @GetMapping("/statistics")
    public ResponseDTO<Map<String, Object>> getRuleStatistics() {
        long total = ruleRepository.count();
        long enabledCount = ruleRepository.findByEnabled(true).size();
        long singleCount = ruleRepository.findByRuleTypeAndEnabled("SINGLE_THRESHOLD", true).size();
        long trendCount = ruleRepository.findByRuleTypeAndEnabled("TREND", true).size();
        long compoundCount = ruleRepository.findByRuleTypeAndEnabled("COMPOUND", true).size();

        return ResponseDTO.success(Map.of(
                "total", total,
                "enabled", enabledCount,
                "disabled", total - enabledCount,
                "singleThresholdRules", singleCount,
                "trendRules", trendCount,
                "compoundRules", compoundCount
        ));
    }
}
