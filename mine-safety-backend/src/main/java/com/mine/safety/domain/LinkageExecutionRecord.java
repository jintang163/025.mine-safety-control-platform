package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "linkage_execution_records")
public class LinkageExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_no", length = 64, nullable = false, unique = true)
    private String executionNo;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_code", length = 64)
    private String ruleCode;

    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "action_id", nullable = false)
    private Long actionId;

    @Column(name = "action_code", length = 64, nullable = false)
    private String actionCode;

    @Column(name = "action_type", length = 32, nullable = false)
    private String actionType;

    @Column(name = "target_type", length = 32)
    private String targetType;

    @Column(name = "target_code", length = 64)
    private String targetCode;

    @Column(name = "action_params", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> actionParams;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "status")
    private Integer status;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "execution_start_time")
    private LocalDateTime executionStartTime;

    @Column(name = "execution_end_time")
    private LocalDateTime executionEndTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_msg", length = 1024)
    private String errorMsg;

    @Column(name = "operator", length = 64)
    private String operator;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = 0;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

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
