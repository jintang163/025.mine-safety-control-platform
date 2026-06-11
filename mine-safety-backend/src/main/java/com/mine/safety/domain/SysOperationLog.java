package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("operation_no")
    private String operationNo;

    @TableField("user_id")
    private Long userId;

    @TableField("username")
    private String username;

    @TableField("real_name")
    private String realName;

    @TableField("operation_type")
    private String operationType;

    @TableField("operation_module")
    private String operationModule;

    @TableField("operation_desc")
    private String operationDesc;

    @TableField("request_method")
    private String requestMethod;

    @TableField("request_url")
    private String requestUrl;

    @TableField("request_params")
    private String requestParams;

    @TableField("response_result")
    private String responseResult;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("user_agent")
    private String userAgent;

    @TableField("cost_time")
    private Long costTime;

    @TableField("status")
    private Integer status = 1;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("target_id")
    private String targetId;

    @TableField("target_type")
    private String targetType;

    @TableField("old_value")
    private String oldValue;

    @TableField("new_value")
    private String newValue;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public enum OperationType {
        CREATE("CREATE", "新增"),
        UPDATE("UPDATE", "修改"),
        DELETE("DELETE", "删除"),
        QUERY("QUERY", "查询"),
        APPROVE("APPROVE", "审批"),
        REJECT("REJECT", "拒绝"),
        ACKNOWLEDGE("ACKNOWLEDGE", "确认"),
        DISPOSAL("DISPOSAL", "处置"),
        LOGIN("LOGIN", "登录"),
        LOGOUT("LOGOUT", "登出"),
        EXPORT("EXPORT", "导出"),
        IMPORT("IMPORT", "导入"),
        UPLOAD("UPLOAD", "上传"),
        DOWNLOAD("DOWNLOAD", "下载");

        private final String code;
        private final String name;

        OperationType(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }
    }

    public enum OperationModule {
        USER("USER", "用户管理"),
        ROLE("ROLE", "角色管理"),
        PERMISSION("PERMISSION", "权限管理"),
        SENSOR("SENSOR", "传感器管理"),
        ALERT("ALERT", "报警管理"),
        THRESHOLD("THRESHOLD", "阈值管理"),
        ALERT_RULE("ALERT_RULE", "报警规则"),
        LINKAGE("LINKAGE", "联动控制"),
        PLC_DEVICE("PLC_DEVICE", "PLC设备"),
        REPORT("REPORT", "报表管理"),
        SYSTEM_CONFIG("SYSTEM_CONFIG", "系统配置"),
        DEVICE_FAULT("DEVICE_FAULT", "设备故障"),
        MAINTENANCE("MAINTENANCE", "维修保养"),
        OPERATION_LOG("OPERATION_LOG", "操作日志");

        private final String code;
        private final String name;

        OperationModule(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }
    }
}
