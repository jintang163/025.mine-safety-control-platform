package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.domain.LinkageAction;
import com.mine.safety.domain.LinkageAction.ExecutionMode;
import com.mine.safety.domain.LinkageExecutionRecord;
import com.mine.safety.domain.LinkageExecutionRecord.Status;
import com.mine.safety.drools.RuleSensorData;
import com.mine.safety.dto.AlertDTO;
import com.mine.safety.repository.LinkageExecutionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkageActionEngineService {

    private final LinkageExecutionRecordRepository executionRecordRepository;
    private final PlcControlService plcControlService;
    private final MessagePushService messagePushService;
    private final ThreadPoolTaskExecutor linkageActionExecutor;
    private final Map<String, LinkageActionHandler> actionHandlers;

    public void executeActions(List<LinkageAction> actions, RuleSensorData ruleData, AlertDTO alert,
                              Long ruleId, String ruleCode) {
        if (actions == null || actions.isEmpty()) {
            return;
        }

        log.info("开始执行联动动作 - 规则: {}, 传感器: {}, 动作数量: {}", ruleCode, ruleData.getSensorId(), actions.size());

        Map<ExecutionMode, List<LinkageAction>> grouped = groupActionsByMode(actions);

        List<LinkageAction> parallelActions = grouped.getOrDefault(ExecutionMode.PARALLEL, Collections.emptyList());
        List<LinkageAction> serialActions = grouped.getOrDefault(ExecutionMode.SERIAL, Collections.emptyList());

        if (!parallelActions.isEmpty()) {
            executeParallelActions(parallelActions, ruleData, alert, ruleId, ruleCode);
        }

        if (!serialActions.isEmpty()) {
            executeSerialActions(serialActions, ruleData, alert, ruleId, ruleCode);
        }
    }

    private Map<ExecutionMode, List<LinkageAction>> groupActionsByMode(List<LinkageAction> actions) {
        Map<ExecutionMode, List<LinkageAction>> grouped = new EnumMap<>(ExecutionMode.class);
        for (LinkageAction action : actions) {
            ExecutionMode mode = ExecutionMode.valueOf(action.getExecutionMode());
            grouped.computeIfAbsent(mode, k -> new ArrayList<>()).add(action);
        }
        return grouped;
    }

    @Async("linkageActionExecutor")
    public void executeParallelActions(List<LinkageAction> actions, RuleSensorData ruleData, AlertDTO alert,
                                    Long ruleId, String ruleCode) {

        List<CompletableFuture<LinkageExecutionRecord>> futures = new ArrayList<>();

        for (LinkageAction action : actions) {
            CompletableFuture<LinkageExecutionRecord>> future = CompletableFuture.supplyAsync(() ->
                    executeActionWithRetry(action, ruleData, alert, ruleId, ruleCode),
                    linkageActionExecutor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])
                .thenRun(() -> log.info("所有并行联动动作执行完成 - 规则: {}, 数量: {}", ruleCode, actions.size()))
                .exceptionally(ex -> {
                    log.error("并行联动动作执行异常 - 规则: {}", ruleCode, ex);
                    return null;
                });
    }

    @Async("linkageActionExecutor")
    public void executeSerialActions(List<LinkageAction> actions, RuleSensorData ruleData, AlertDTO alert,
                                  Long ruleId, String ruleCode) {

        log.info("开始串行执行联动动作 - 规则: {}", ruleCode);

        for (LinkageAction action : actions) {
            try {
                LinkageExecutionRecord record = executeActionWithRetry(action, ruleData, alert, ruleId, ruleCode);
                if (record.getStatus() != Status.SUCCESS.getCode() && action.getPriority() != null && action.getPriority() >= 2) {
                    log.warn("高优先级动作执行失败，终止后续串行动作 - 动作: {}", action.getActionCode());
                    break;
                }
            } catch (Exception e) {
                log.error("串行联动动作执行异常 - 动作: {}", action.getActionCode(), e);
                if (action.getPriority() != null && action.getPriority() >= 2) {
                    break;
                }
            }
        }

        log.info("串行联动动作执行完成 - 规则: {}", ruleCode);
    }

    public LinkageExecutionRecord executeActionWithRetry(LinkageAction action, RuleSensorData ruleData, AlertDTO alert,
                                                Long ruleId, String ruleCode) {

        LinkageExecutionRecord record = createExecutionRecord(action, ruleData, alert, ruleId, ruleCode);
        int maxRetry = action.getMaxRetry() != null ? action.getMaxRetry() : 3;
        int retryInterval = action.getRetryIntervalSeconds() != null ? action.getRetryIntervalSeconds() : 5;

        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                updateRecordStatus(record, Status.EXECUTING, attempt, null);

                long startTime = System.currentTimeMillis();
                ActionResult result = executeSingleAction(action, ruleData, alert);
                long duration = System.currentTimeMillis() - startTime;

                record.setDurationMs(duration);
                record.setExecutionEndTime(LocalDateTime.now());

                if (result.isSuccess()) {
                    updateRecordStatus(record, Status.SUCCESS, attempt, null);
                    record.setResponsePayload(result.getResponse());
                    log.info("联动动作执行成功 - 动作: {}, 耗时: {}ms", action.getActionCode(), duration);
                    return record;
                } else {
                    throw new ActionExecutionException(result.getErrorMessage());
                }

            } catch (Exception e) {
                log.warn("联动动作执行失败 - 动作: {}, 尝试: {}/{}", action.getActionCode(), attempt, maxRetry, e);

                if (attempt < maxRetry) {
                    try {
                        Thread.sleep(retryInterval * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                        record.setErrorMsg(e.getMessage());
                        updateRecordStatus(record, Status.FAILED, attempt, e.getMessage());
                }
            }
        }

        return record;
    }

    private ActionResult executeSingleAction(LinkageAction action, RuleSensorData ruleData, AlertDTO alert) {
        String actionType = action.getActionType();
        Map<String, Object> params = action.getActionParams();

        String targetZone = resolveTargetCode(action, ruleData);

        try {
            LinkageActionHandler handler = actionHandlers.get(actionType);
            if (handler != null) {
                return handler.execute(action, params, ruleData, alert, targetZone);
            }

            return switch (actionType) {
                case "SOUND_LIGHT_ALARM" -> plcControlService.triggerSoundLightAlarm(targetZone, params);
                case "VOICE_BROADCAST" -> plcControlService.triggerVoiceBroadcast(targetZone, params);
                case "REMOTE_POWER_OFF" -> plcControlService.triggerRemotePowerOff(targetZone, params);
                case "MESSAGE_PUSH" -> messagePushService.pushAlertMessage(alert, params);
                case "VIDEO_POPUP" -> triggerVideoPopup(targetZone, params);
                default -> ActionResult.success("No handler found but action type: " + actionType);
            };
        } catch (Exception e) {
            return ActionResult.failure("Execution failed: " + e.getMessage());
        }
    }

    private String resolveTargetCode(LinkageAction action, RuleSensorData ruleData) {
        if (action.getTargetCode() != null && !action.getTargetCode().isEmpty()) {
            return action.getTargetCode();
        }
        return ruleData.getZoneCode();
    }

    private LinkageExecutionRecord createExecutionRecord(LinkageAction action, RuleSensorData ruleData,
                                                 AlertDTO alert, Long ruleId, String ruleCode) {
        String executionNo = "EXEC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

        LinkageExecutionRecord record = LinkageExecutionRecord.builder()
                .executionNo(executionNo)
                .ruleId(ruleId)
                .ruleCode(ruleCode)
                .alertId(alert != null ? alert.getId() : null)
                .actionId(action.getId())
                .actionCode(action.getActionCode())
                .actionType(action.getActionType())
                .targetType(action.getTargetType())
                .targetCode(resolveTargetCode(action, ruleData))
                .actionParams(action.getActionParams())
                .requestPayload(buildRequestPayload(action, ruleData, alert))
                .status(Status.PENDING.getCode())
                .retryCount(0)
                .executionStartTime(LocalDateTime.now())
                .operator("SYSTEM")
                .build();

        return executionRecordRepository.insert(record);
    }

    private String buildRequestPayload(LinkageAction action, RuleSensorData ruleData, AlertDTO alert) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action.getActionCode());
        payload.put("actionType", action.getActionType());
        payload.put("params", action.getActionParams());
        payload.put("sensorId", ruleData.getSensorId());
        payload.put("sensorType", ruleData.getSensorType());
        payload.put("zoneCode", ruleData.getZoneCode());
        payload.put("value", ruleData.getValue());
        if (alert != null) {
            payload.put("alert", Map.of(
                    "alertNo", alert.getAlertNo(),
                    "level", alert.getLevel(),
                    "description", alert.getDescription()
            ));
        }
        return JSON.toJSONString(payload);
    }

    private void updateRecordStatus(LinkageExecutionRecord record, Status status, int retryCount, String errorMsg) {
        record.setStatus(status.getCode());
        record.setRetryCount(retryCount);
        if (errorMsg != null) {
            record.setErrorMsg(errorMsg);
        }
        executionRecordRepository.updateById(record);
    }

    private ActionResult triggerVideoPopup(String targetZone, Map<String, Object> params) {
        log.info("触发视频监控弹出 - 区域: {}, 参数: {}", targetZone, params);
        return ActionResult.success("Video popup triggered for zone: " + targetZone);
    }

    @FunctionalInterface
    public interface LinkageActionHandler {
        ActionResult execute(LinkageAction action, Map<String, Object> params,
                           RuleSensorData ruleData, AlertDTO alert, String targetZone);
    }

    public static class ActionResult {
        private final boolean success;
        private final String response;
        private final String errorMessage;

        private ActionResult(boolean success, String response, String errorMessage) {
            this.success = success;
            this.response = response;
            this.errorMessage = errorMessage;
        }

        public static ActionResult success(String response) {
            return new ActionResult(true, response, null);
        }

        public static ActionResult failure(String errorMessage) {
            return new ActionResult(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getResponse() {
            return response;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static class ActionExecutionException extends RuntimeException {
        public ActionExecutionException(String message) {
            super(message);
        }
    }
}
