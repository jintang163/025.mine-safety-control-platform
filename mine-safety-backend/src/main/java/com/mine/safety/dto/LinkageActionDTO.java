package com.mine.safety.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import com.mine.safety.domain.LinkageAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkageActionDTO {

    private Long id;

    private String actionCode;

    private String actionName;

    private String actionType;

    private String targetType;

    private String targetCode;

    private Map<String, Object> actionParams;

    private String executionMode;

    private Integer priority;

    private Integer timeoutSeconds;

    private Integer maxRetry;

    private Integer retryIntervalSeconds;

    private Boolean enabled;

    private String description;

    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public static LinkageActionDTO fromEntity(LinkageAction entity) {
        if (entity == null) {
            return null;
        }
        return LinkageActionDTO.builder()
                .id(entity.getId())
                .actionCode(entity.getActionCode())
                .actionName(entity.getActionName())
                .actionType(entity.getActionType())
                .targetType(entity.getTargetType())
                .targetCode(entity.getTargetCode())
                .actionParams(entity.getActionParams())
                .executionMode(entity.getExecutionMode())
                .priority(entity.getPriority())
                .timeoutSeconds(entity.getTimeoutSeconds())
                .maxRetry(entity.getMaxRetry())
                .retryIntervalSeconds(entity.getRetryIntervalSeconds())
                .enabled(entity.getEnabled())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public LinkageAction toEntity() {
        return LinkageAction.builder()
                .id(id)
                .actionCode(actionCode)
                .actionName(actionName)
                .actionType(actionType)
                .targetType(targetType)
                .targetCode(targetCode)
                .actionParams(actionParams)
                .executionMode(executionMode)
                .priority(priority)
                .timeoutSeconds(timeoutSeconds)
                .maxRetry(maxRetry)
                .retryIntervalSeconds(retryIntervalSeconds)
                .enabled(enabled)
                .description(description)
                .build();
    }
}
