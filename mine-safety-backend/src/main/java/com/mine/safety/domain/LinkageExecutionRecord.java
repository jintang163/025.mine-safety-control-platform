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
@TableName(value = "linkage_execution_records", autoResultMap = true)
public class LinkageExecutionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("execution_no")
    private String executionNo;

    @TableField("rule_id")
    private Long ruleId;

    @TableField("rule_code")
    private String ruleCode;

    @TableField("alert_id")
    private Long alertId;

    @TableField("action_id")
    private Long actionId;

    @TableField("action_code")
    private String actionCode;

    @TableField("action_type")
    private String actionType;

    @TableField("target_type")
    private String targetType;

    @TableField("target_code")
    private String targetCode;

    @TableField(value = "action_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> actionParams;

    @TableField("request_payload")
    private String requestPayload;

    @TableField("response_payload")
    private String responsePayload;

    @TableField("status")
    private Integer status = 0;

    @TableField("retry_count")
    private Integer retryCount = 0;

    @TableField("execution_start_time")
    private LocalDateTime executionStartTime;

    @TableField("execution_end_time")
    private LocalDateTime executionEndTime;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("operator")
    private String operator;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public enum Status {
        PENDING(0, "待执行"),
        EXECUTING(1, "执行中"),
        SUCCESS(2, "成功"),
        FAILED(3, "失败"),
        TIMEOUT(4, "超时"),
        CANCELLED(5, "已取消");

        private final int code;
        private final String desc;

        Status(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public int getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }
    }
}
