package com.mine.safety.controller;

import com.mine.safety.domain.LinkageAction;
import com.mine.safety.dto.LinkageActionDTO;
import com.mine.safety.dto.ResponseDTO;
import com.mine.safety.repository.LinkageActionRepository;
import com.mine.safety.repository.RuleActionRelationRepository;
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
@RequestMapping("/api/linkage/actions")
@RequiredArgsConstructor
public class LinkageActionController {

    private final LinkageActionRepository actionRepository;
    private final RuleActionRelationRepository relationRepository;

    @GetMapping
    public ResponseDTO<Page<LinkageActionDTO>> getActions(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<LinkageAction> page = actionRepository.findAll(pageable);
        return ResponseDTO.success(page.map(LinkageActionDTO::fromEntity));
    }

    @GetMapping("/enabled")
    public ResponseDTO<List<LinkageActionDTO>> getEnabledActions() {
        List<LinkageAction> actions = actionRepository.findByEnabled(true);
        return ResponseDTO.success(actions.stream().map(LinkageActionDTO::fromEntity).toList());
    }

    @GetMapping("/type/{actionType}")
    public ResponseDTO<List<LinkageActionDTO>> getActionsByType(@PathVariable String actionType) {
        List<LinkageAction> actions = actionRepository.findByActionTypeAndEnabled(actionType, true);
        return ResponseDTO.success(actions.stream().map(LinkageActionDTO::fromEntity).toList());
    }

    @GetMapping("/rule/{ruleId}")
    public ResponseDTO<List<LinkageActionDTO>> getActionsByRuleId(@PathVariable Long ruleId) {
        List<LinkageAction> actions = actionRepository.findActionsByRuleId(ruleId);
        return ResponseDTO.success(actions.stream().map(LinkageActionDTO::fromEntity).toList());
    }

    @GetMapping("/{id}")
    public ResponseDTO<LinkageActionDTO> getActionById(@PathVariable Long id) {
        return actionRepository.findById(id)
                .map(action -> ResponseDTO.success(LinkageActionDTO.fromEntity(action)))
                .orElse(ResponseDTO.error("动作不存在"));
    }

    @GetMapping("/code/{actionCode}")
    public ResponseDTO<LinkageActionDTO> getActionByCode(@PathVariable String actionCode) {
        return actionRepository.findByActionCode(actionCode)
                .map(action -> ResponseDTO.success(LinkageActionDTO.fromEntity(action)))
                .orElse(ResponseDTO.error("动作不存在"));
    }

    @PostMapping
    public ResponseDTO<LinkageActionDTO> createAction(@RequestBody LinkageActionDTO dto) {
        if (actionRepository.existsByActionCode(dto.getActionCode())) {
            return ResponseDTO.error("动作编码已存在");
        }

        LinkageAction action = dto.toEntity();
        action = actionRepository.save(action);

        log.info("创建联动动作成功 - 动作: {}, 编码: {}", action.getActionName(), action.getActionCode());
        return ResponseDTO.success(LinkageActionDTO.fromEntity(action));
    }

    @PutMapping("/{id}")
    public ResponseDTO<LinkageActionDTO> updateAction(@PathVariable Long id, @RequestBody LinkageActionDTO dto) {
        return actionRepository.findById(id)
                .map(action -> {
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

                    LinkageAction saved = actionRepository.save(action);
                    log.info("更新联动动作成功 - 动作: {}", saved.getActionCode());
                    return ResponseDTO.success(LinkageActionDTO.fromEntity(saved));
                })
                .orElse(ResponseDTO.error("动作不存在"));
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> deleteAction(@PathVariable Long id) {
        if (!actionRepository.existsById(id)) {
            return ResponseDTO.error("动作不存在");
        }

        relationRepository.deleteByActionId(id);
        actionRepository.deleteById(id);
        log.info("删除联动动作成功 - ID: {}", id);
        return ResponseDTO.success();
    }

    @GetMapping("/statistics")
    public ResponseDTO<Map<String, Object>> getActionStatistics() {
        long total = actionRepository.count();
        long enabledCount = actionRepository.findByEnabled(true).size();
        long soundLightCount = actionRepository.findByActionTypeAndEnabled("SOUND_LIGHT_ALARM", true).size();
        long broadcastCount = actionRepository.findByActionTypeAndEnabled("VOICE_BROADCAST", true).size();
        long powerOffCount = actionRepository.findByActionTypeAndEnabled("REMOTE_POWER_OFF", true).size();
        long messageCount = actionRepository.findByActionTypeAndEnabled("MESSAGE_PUSH", true).size();
        long videoCount = actionRepository.findByActionTypeAndEnabled("VIDEO_POPUP", true).size();

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
