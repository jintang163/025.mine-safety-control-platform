package com.mine.safety.controller;

import com.mine.safety.domain.AlertRuleDefinition;
import com.mine.safety.domain.RuleActionRelation;
import com.mine.safety.dto.AlertRuleDTO;
import com.mine.safety.dto.ResponseDTO;
import com.mine.safety.repository.AlertRuleDefinitionRepository;
import com.mine.safety.repository.RuleActionRelationRepository;
import com.mine.safety.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAuthority('alert:view')")
    public ResponseDTO<IPage<AlertRuleDTO>> getRules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<AlertRuleDefinition> entityPage = ruleRepository.selectPage(new Page<>(page, size), null);
        Page<AlertRuleDTO> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(entityPage.getRecords().stream().map(AlertRuleDTO::fromEntity).toList());
        return ResponseDTO.success(result);
    }

    @GetMapping("/enabled")
    @PreAuthorize("hasAuthority('alert:view')")
    public ResponseDTO<List<AlertRuleDTO>> getEnabledRules() {
        List<AlertRuleDefinition> rules = ruleEngineService.getEnabledRules();
        return ResponseDTO.success(rules.stream().map(AlertRuleDTO::fromEntity).toList());
    }

    @GetMapping("/type/{ruleType}")
    @PreAuthorize("hasAuthority('alert:view')")
    public ResponseDTO<List<AlertRuleDTO>> getRulesByType(@PathVariable String ruleType) {
        List<AlertRuleDefinition> rules = ruleRepository.selectList(
                new LambdaQueryWrapper<AlertRuleDefinition>().eq(AlertRuleDefinition::getRuleType, ruleType).eq(AlertRuleDefinition::getEnabled, true));
        return ResponseDTO.success(rules.stream().map(AlertRuleDTO::fromEntity).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('alert:view')")
    public ResponseDTO<AlertRuleDTO> getRuleById(@PathVariable Long id) {
        AlertRuleDefinition rule = ruleRepository.selectById(id);
        if (rule == null) {
            return ResponseDTO.error("规则不存在");
        }
        return ResponseDTO.success(AlertRuleDTO.fromEntity(rule));
    }

    @GetMapping("/code/{ruleCode}")
    @PreAuthorize("hasAuthority('alert:view')")
    public ResponseDTO<AlertRuleDTO> getRuleByCode(@PathVariable String ruleCode) {
        AlertRuleDefinition rule = ruleRepository.selectOne(
                new LambdaQueryWrapper<AlertRuleDefinition>().eq(AlertRuleDefinition::getRuleCode, ruleCode));
        if (rule == null) {
            return ResponseDTO.error("规则不存在");
        }
        return ResponseDTO.success(AlertRuleDTO.fromEntity(rule));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('alert:edit')")
    public ResponseDTO<AlertRuleDTO> createRule(@RequestBody AlertRuleDTO dto) {
        if (ruleRepository.selectCount(new LambdaQueryWrapper<AlertRuleDefinition>().eq(AlertRuleDefinition::getRuleCode, dto.getRuleCode())) > 0) {
            return ResponseDTO.error("规则编码已存在");
        }

        AlertRuleDefinition rule = dto.toEntity();
        rule.setVersion(1);
        ruleRepository.insert(rule);

        log.info("创建规则成功 - 规则: {}, 编码: {}", rule.getRuleName(), rule.getRuleCode());
        return ResponseDTO.success(AlertRuleDTO.fromEntity(rule));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('alert:edit')")
    public ResponseDTO<AlertRuleDTO> updateRule(@PathVariable Long id, @RequestBody AlertRuleDTO dto) {
        AlertRuleDefinition rule = ruleRepository.selectById(id);
        if (rule == null) {
            return ResponseDTO.error("规则不存在");
        }

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

        ruleRepository.updateById(rule);
        log.info("更新规则成功 - 规则: {}", rule.getRuleCode());
        return ResponseDTO.success(AlertRuleDTO.fromEntity(rule));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('alert:edit')")
    public ResponseDTO<Void> deleteRule(@PathVariable Long id) {
        if (ruleRepository.selectById(id) == null) {
            return ResponseDTO.error("规则不存在");
        }

        relationRepository.delete(new LambdaQueryWrapper<RuleActionRelation>().eq(RuleActionRelation::getRuleId, id));
        ruleRepository.deleteById(id);
        log.info("删除规则成功 - ID: {}", id);
        return ResponseDTO.success();
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('alert:edit')")
    public ResponseDTO<AlertRuleDTO> enableRule(@PathVariable Long id) {
        AlertRuleDefinition rule = ruleRepository.selectById(id);
        if (rule == null) {
            return ResponseDTO.error("规则不存在");
        }
        rule.setEnabled(true);
        ruleRepository.updateById(rule);
        log.info("启用规则 - 编码: {}", rule.getRuleCode());
        return ResponseDTO.success(AlertRuleDTO.fromEntity(rule));
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('alert:edit')")
    public ResponseDTO<AlertRuleDTO> disableRule(@PathVariable Long id) {
        AlertRuleDefinition rule = ruleRepository.selectById(id);
        if (rule == null) {
            return ResponseDTO.error("规则不存在");
        }
        rule.setEnabled(false);
        ruleRepository.updateById(rule);
        log.info("禁用规则 - 编码: {}", rule.getRuleCode());
        return ResponseDTO.success(AlertRuleDTO.fromEntity(rule));
    }

    @PostMapping("/reload")
    @PreAuthorize("hasAuthority('system:config')")
    public ResponseDTO<Map<String, Object>> reloadRules() {
        ruleEngineService.reloadRules();
        int count = ruleEngineService.getEnabledRules().size();
        return ResponseDTO.success(Map.of(
                "message", "规则重新加载成功",
                "enabledRulesCount", count
        ));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('alert:view')")
    public ResponseDTO<Map<String, Object>> getRuleStatistics() {
        long total = ruleRepository.selectCount(null);
        long enabledCount = ruleRepository.selectCount(new LambdaQueryWrapper<AlertRuleDefinition>().eq(AlertRuleDefinition::getEnabled, true));
        long singleCount = ruleRepository.selectCount(new LambdaQueryWrapper<AlertRuleDefinition>().eq(AlertRuleDefinition::getRuleType, "SINGLE_THRESHOLD").eq(AlertRuleDefinition::getEnabled, true));
        long trendCount = ruleRepository.selectCount(new LambdaQueryWrapper<AlertRuleDefinition>().eq(AlertRuleDefinition::getRuleType, "TREND").eq(AlertRuleDefinition::getEnabled, true));
        long compoundCount = ruleRepository.selectCount(new LambdaQueryWrapper<AlertRuleDefinition>().eq(AlertRuleDefinition::getRuleType, "COMPOUND").eq(AlertRuleDefinition::getEnabled, true));

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
