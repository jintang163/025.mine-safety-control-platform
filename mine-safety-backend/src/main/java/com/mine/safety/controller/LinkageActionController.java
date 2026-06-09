package com.mine.safety.controller;

import com.mine.safety.domain.LinkageAction;
import com.mine.safety.domain.RuleActionRelation;
import com.mine.safety.dto.LinkageActionDTO;
import com.mine.safety.dto.ResponseDTO;
import com.mine.safety.repository.LinkageActionRepository;
import com.mine.safety.repository.RuleActionRelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/linkage/actions")
@RequiredArgsConstructor
public class LinkageActionController {

    private final LinkageActionRepository actionRepository;
    private final RuleActionRelationRepository relationRepository;

    @GetMapping
    public ResponseDTO<IPage<LinkageActionDTO>> getActions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<LinkageAction> entityPage = actionRepository.selectPage(new Page<>(page, size), null);
        Page<LinkageActionDTO> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(entityPage.getRecords().stream().map(LinkageActionDTO::fromEntity).toList());
        return ResponseDTO.success(result);
    }

    @GetMapping("/enabled")
    public ResponseDTO<List<LinkageActionDTO>> getEnabledActions() {
        List<LinkageAction> actions = actionRepository.selectList(
                new LambdaQueryWrapper<LinkageAction>().eq(LinkageAction::getEnabled, true));
        return ResponseDTO.success(actions.stream().map(LinkageActionDTO::fromEntity).toList());
    }

    @GetMapping("/type/{actionType}")
    public ResponseDTO<List<LinkageActionDTO>> getActionsByType(@PathVariable String actionType) {
        List<LinkageAction> actions = actionRepository.selectList(
                new LambdaQueryWrapper<LinkageAction>().eq(LinkageAction::getActionType, actionType).eq(LinkageAction::getEnabled, true));
        return ResponseDTO.success(actions.stream().map(LinkageActionDTO::fromEntity).toList());
    }

    @GetMapping("/rule/{ruleId}")
    public ResponseDTO<List<LinkageActionDTO>> getActionsByRuleId(@PathVariable Long ruleId) {
        List<LinkageAction> actions = actionRepository.findActionsByRuleId(ruleId);
        return ResponseDTO.success(actions.stream().map(LinkageActionDTO::fromEntity).toList());
    }

    @GetMapping("/{id}")
    public ResponseDTO<LinkageActionDTO> getActionById(@PathVariable Long id) {
        LinkageAction action = actionRepository.selectById(id);
        if (action == null) {
            return ResponseDTO.error("动作不存在");
        }
        return ResponseDTO.success(LinkageActionDTO.fromEntity(action));
    }

    @GetMapping("/code/{actionCode}")
    public ResponseDTO<LinkageActionDTO> getActionByCode(@PathVariable String actionCode) {
        LinkageAction action = actionRepository.selectOne(
                new LambdaQueryWrapper<LinkageAction>().eq(LinkageAction::getActionCode, actionCode));
        if (action == null) {
            return ResponseDTO.error("动作不存在");
        }
        return ResponseDTO.success(LinkageActionDTO.fromEntity(action));
    }

    @PostMapping
    public ResponseDTO<LinkageActionDTO> createAction(@RequestBody LinkageActionDTO dto) {
        if (actionRepository.selectCount(new LambdaQueryWrapper<LinkageAction>().eq(LinkageAction::getActionCode, dto.getActionCode())) > 0) {
            return ResponseDTO.error("动作编码已存在");
        }

        LinkageAction action = dto.toEntity();
        actionRepository.insert(action);

        log.info("创建联动动作成功 - 动作: {}, 编码: {}", action.getActionName(), action.getActionCode());
        return ResponseDTO.success(LinkageActionDTO.fromEntity(action));
    }

    @PutMapping("/{id}")
    public ResponseDTO<LinkageActionDTO> updateAction(@PathVariable Long id, @RequestBody LinkageActionDTO dto) {
        LinkageAction action = actionRepository.selectById(id);
        if (action == null) {
            return ResponseDTO.error("动作不存在");
        }

        action.setActionName(dto.getActionName());
        action.setActionType(dto.getActionType());
        action.setTargetType(dto.getTargetType());
        action.setTargetCode(dto.getTargetCode());
        action.setActionParams(dto.getActionParams());
        action.setExecutionMode(dto.getExecutionMode());
        action.setPriority(dto.getPriority());
        action.setTimeoutSeconds(dto.getTimeoutSeconds());
        action.setMaxRetry(dto.getMaxRetry());
        action.setRetryIntervalSeconds(dto.getRetryIntervalSeconds());
        action.setEnabled(dto.getEnabled());
        action.setDescription(dto.getDescription());

        actionRepository.updateById(action);
        log.info("更新联动动作成功 - 动作: {}", action.getActionCode());
        return ResponseDTO.success(LinkageActionDTO.fromEntity(action));
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> deleteAction(@PathVariable Long id) {
        if (actionRepository.selectById(id) == null) {
            return ResponseDTO.error("动作不存在");
        }

        relationRepository.delete(new LambdaQueryWrapper<RuleActionRelation>().eq(RuleActionRelation::getActionId, id));
        actionRepository.deleteById(id);
        log.info("删除联动动作成功 - ID: {}", id);
        return ResponseDTO.success();
    }

    @GetMapping("/statistics")
    public ResponseDTO<Map<String, Object>> getActionStatistics() {
        long total = actionRepository.selectCount(null);
        long enabledCount = actionRepository.selectCount(new LambdaQueryWrapper<LinkageAction>().eq(LinkageAction::getEnabled, true));
        long soundLightCount = actionRepository.selectCount(new LambdaQueryWrapper<LinkageAction>().eq(LinkageAction::getActionType, "SOUND_LIGHT_ALARM").eq(LinkageAction::getEnabled, true));
        long broadcastCount = actionRepository.selectCount(new LambdaQueryWrapper<LinkageAction>().eq(LinkageAction::getActionType, "VOICE_BROADCAST").eq(LinkageAction::getEnabled, true));
        long powerOffCount = actionRepository.selectCount(new LambdaQueryWrapper<LinkageAction>().eq(LinkageAction::getActionType, "REMOTE_POWER_OFF").eq(LinkageAction::getEnabled, true));
        long messageCount = actionRepository.selectCount(new LambdaQueryWrapper<LinkageAction>().eq(LinkageAction::getActionType, "MESSAGE_PUSH").eq(LinkageAction::getEnabled, true));
        long videoCount = actionRepository.selectCount(new LambdaQueryWrapper<LinkageAction>().eq(LinkageAction::getActionType, "VIDEO_POPUP").eq(LinkageAction::getEnabled, true));

        return ResponseDTO.success(Map.of(
                "total", total,
                "enabled", enabledCount,
                "soundLightAlarms", soundLightCount,
                "voiceBroadcasts", broadcastCount,
                "remotePowerOffs", powerOffCount,
                "messagePushes", messageCount,
                "videoPopups", videoCount
        ));
    }
}
