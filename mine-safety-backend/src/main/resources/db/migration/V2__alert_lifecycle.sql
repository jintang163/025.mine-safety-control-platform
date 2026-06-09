-- 报警事件全生命周期管理 - 数据库迁移脚本
-- 执行前请备份 alerts 表数据

-- 1. alerts 表新增字段
ALTER TABLE alerts ADD COLUMN tunnel VARCHAR(128) COMMENT '巷道' AFTER location;
ALTER TABLE alerts ADD COLUMN threshold_type VARCHAR(32) COMMENT '阈值类型' AFTER threshold_value;
ALTER TABLE alerts ADD COLUMN escalation_level VARCHAR(16) DEFAULT 'DUTY' COMMENT '升级层级: DUTY/SHIFT_LEADER/MINE_MANAGER' AFTER status;
ALTER TABLE alerts ADD COLUMN escalation_time DATETIME COMMENT '升级时间' AFTER escalation_level;
ALTER TABLE alerts ADD COLUMN confirmed_by VARCHAR(64) COMMENT '确认人' AFTER escalation_time;
ALTER TABLE alerts ADD COLUMN confirmed_at DATETIME COMMENT '确认时间' AFTER confirmed_by;
ALTER TABLE alerts ADD COLUMN processing_by VARCHAR(64) COMMENT '处置人' AFTER confirmed_at;
ALTER TABLE alerts ADD COLUMN processing_at DATETIME COMMENT '处置开始时间' AFTER processing_by;
ALTER TABLE alerts ADD COLUMN recovered_at DATETIME COMMENT '恢复时间' AFTER processing_at;
ALTER TABLE alerts ADD COLUMN closed_by VARCHAR(64) COMMENT '关闭人' AFTER recovered_at;
ALTER TABLE alerts ADD COLUMN closed_at DATETIME COMMENT '关闭时间' AFTER closed_by;

-- 新增索引
CREATE INDEX idx_escalation_level ON alerts(escalation_level);
CREATE INDEX idx_tunnel ON alerts(tunnel);
CREATE INDEX idx_threshold_type ON alerts(threshold_type);

-- 2. 报警处置记录表
CREATE TABLE IF NOT EXISTS alert_disposal_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_no VARCHAR(64) NOT NULL COMMENT '报警编号',
    disposal_type VARCHAR(32) NOT NULL COMMENT '处置类型: CONFIRM/PROCESS/RECOVER/CLOSE',
    disposal_measures VARCHAR(1024) NOT NULL COMMENT '处置措施',
    image_urls VARCHAR(2048) COMMENT '现场图片URL，多个用逗号分隔',
    operator VARCHAR(64) NOT NULL COMMENT '操作人',
    operator_role VARCHAR(32) COMMENT '操作人角色',
    recovery_value DECIMAL(12,4) COMMENT '恢复时的传感器数值',
    recovery_time DATETIME COMMENT '恢复时间',
    remark VARCHAR(512) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_disposal_alert_no (alert_no),
    INDEX idx_disposal_operator (operator)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警处置记录表';

-- 3. 报警升级日志表
CREATE TABLE IF NOT EXISTS alert_escalation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_no VARCHAR(64) NOT NULL COMMENT '报警编号',
    from_level VARCHAR(16) NOT NULL COMMENT '原升级层级',
    to_level VARCHAR(16) NOT NULL COMMENT '目标升级层级',
    escalation_reason VARCHAR(256) COMMENT '升级原因',
    notified_users VARCHAR(512) COMMENT '被通知人员',
    notification_channels VARCHAR(256) COMMENT '通知渠道',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_esca_alert_no (alert_no),
    INDEX idx_esca_level (from_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警升级日志表';
