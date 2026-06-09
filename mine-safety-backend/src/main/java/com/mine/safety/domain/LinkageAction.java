package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "linkage_actions", autoResultMap = true)
public class LinkageAction {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("action_code")
    private String actionCode;

    @TableField("action_name")
    private String actionName;

    @TableField("action_type")
    private String actionType;

    @TableField("target_type")
    private String targetType;

    @TableField("target_code")
    private String targetCode;

    @TableField(value = "action_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> actionParams;

    @TableField("execution_mode")
    private String executionMode = "PARALLEL";

    @TableField("priority")
    private Integer priority = 0;

    @TableField("timeout_seconds")
    private Integer timeoutSeconds = 30;

    @TableField("max_retry")
    private Integer maxRetry = 3;

    @TableField("retry_interval_seconds")
    private Integer retryIntervalSeconds = 5;

    @TableField("enabled")
    private Boolean enabled = true;

    @TableField("description")
    private String description;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum ActionType {
        SOUND_LIGHT_ALARM, VOICE_BROADCAST, REMOTE_POWER_OFF, MESSAGE_PUSH, VIDEO_POPUP
    }

    public enum TargetType {
        ZONE, SENSOR, DEVICE, USER, ROLE
    }

    public enum ExecutionMode {
        SERIAL, PARALLEL
    }
}
