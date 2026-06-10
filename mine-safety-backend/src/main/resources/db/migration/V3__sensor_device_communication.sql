-- 传感器设备与通信管理 - 数据库迁移脚本

ALTER TABLE sensors ADD COLUMN battery_level TINYINT DEFAULT 100 COMMENT '电量百分比: 0-100' AFTER last_online_time;
ALTER TABLE sensors ADD COLUMN signal_strength TINYINT DEFAULT 0 COMMENT '信号强度: 0-100' AFTER battery_level;
ALTER TABLE sensors ADD COLUMN data_upload_delay INT DEFAULT 0 COMMENT '数据上报延迟(ms)' AFTER signal_strength;
ALTER TABLE sensors ADD COLUMN offline_timeout_minutes INT DEFAULT 10 COMMENT '离线超时时间(分钟)' AFTER data_upload_delay;
ALTER TABLE sensors ADD COLUMN calibration_cycle_days INT DEFAULT 90 COMMENT '校验周期(天)' AFTER offline_timeout_minutes;
ALTER TABLE sensors ADD COLUMN last_calibration_date DATE COMMENT '最后校验日期' AFTER calibration_cycle_days;
ALTER TABLE sensors ADD COLUMN next_calibration_date DATE COMMENT '下次校验日期' AFTER last_calibration_date;

CREATE INDEX idx_battery_level ON sensors(battery_level);
CREATE INDEX idx_signal_strength ON sensors(signal_strength);

CREATE TABLE IF NOT EXISTS sensor_comm_params (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id VARCHAR(64) NOT NULL COMMENT '传感器ID',
    param_key VARCHAR(64) NOT NULL COMMENT '参数键',
    param_value VARCHAR(512) NOT NULL COMMENT '参数值',
    param_type VARCHAR(32) DEFAULT 'STRING' COMMENT '参数类型: STRING,NUMBER,JSON',
    description VARCHAR(256) COMMENT '参数描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sensor_param (sensor_id, param_key),
    INDEX idx_sensor_id (sensor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器通讯参数表';

CREATE TABLE IF NOT EXISTS sensor_calibration_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id VARCHAR(64) NOT NULL COMMENT '传感器ID',
    calibration_no VARCHAR(64) NOT NULL UNIQUE COMMENT '校验编号',
    calibration_date DATE NOT NULL COMMENT '校验日期',
    next_calibration_date DATE COMMENT '下次校验日期',
    calibration_type VARCHAR(32) NOT NULL COMMENT '校验类型: ROUTINE-例行, EMERGENCY-临时, RETURN_FACTORY-返厂',
    calibration_result VARCHAR(16) NOT NULL COMMENT '校验结果: QUALIFIED-合格, UNQUALIFIED-不合格',
    calibration_org VARCHAR(128) COMMENT '校验机构',
    calibration_person VARCHAR(64) COMMENT '校验人员',
    certificate_no VARCHAR(64) COMMENT '证书编号',
    deviation_value DECIMAL(10,4) COMMENT '偏差值',
    deviation_unit VARCHAR(16) COMMENT '偏差单位',
    remark VARCHAR(512) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sensor_id (sensor_id),
    INDEX idx_calibration_date (calibration_date),
    INDEX idx_next_calibration (next_calibration_date),
    INDEX idx_calibration_type (calibration_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器校验记录表';

CREATE TABLE IF NOT EXISTS sensor_maintenance_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id VARCHAR(64) NOT NULL COMMENT '传感器ID',
    maintenance_no VARCHAR(64) NOT NULL UNIQUE COMMENT '维保编号',
    maintenance_type VARCHAR(32) NOT NULL COMMENT '维保类型: REPAIR-维修, REPLACE-更换, CLEAN-清洁, INSPECT-巡检, UPGRADE-升级',
    maintenance_date DATETIME NOT NULL COMMENT '维保日期',
    maintenance_person VARCHAR(64) NOT NULL COMMENT '维保人员',
    maintenance_content VARCHAR(1024) NOT NULL COMMENT '维保内容',
    replaced_parts VARCHAR(512) COMMENT '更换部件',
    cost DECIMAL(10,2) COMMENT '费用(元)',
    result VARCHAR(16) NOT NULL COMMENT '维保结果: COMPLETED-完成, PARTIAL-部分完成, FAILED-失败',
    remark VARCHAR(512) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sensor_id (sensor_id),
    INDEX idx_maintenance_date (maintenance_date),
    INDEX idx_maintenance_type (maintenance_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器维保记录表';

CREATE TABLE IF NOT EXISTS sensor_device_shadow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id VARCHAR(64) NOT NULL UNIQUE COMMENT '传感器ID',
    reported_state JSON COMMENT '设备上报状态(JSON)',
    desired_state JSON COMMENT '期望配置状态(JSON)',
    reported_version INT DEFAULT 0 COMMENT '上报状态版本号',
    desired_version INT DEFAULT 0 COMMENT '期望配置版本号',
    last_reported_time DATETIME COMMENT '最后上报时间',
    last_desired_time DATETIME COMMENT '最后下发时间',
    sync_status VARCHAR(16) DEFAULT 'SYNCED' COMMENT '同步状态: SYNCED-已同步, PENDING-待同步, FAILED-同步失败',
    delta JSON COMMENT '差异配置(JSON)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sensor_id (sensor_id),
    INDEX idx_sync_status (sync_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器设备影子表';

CREATE TABLE IF NOT EXISTS device_fault_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE COMMENT '工单编号',
    sensor_id VARCHAR(64) NOT NULL COMMENT '传感器ID',
    sensor_name VARCHAR(128) COMMENT '传感器名称',
    fault_type VARCHAR(32) NOT NULL COMMENT '故障类型: OFFLINE-离线, LOW_BATTERY-低电量, SIGNAL_WEAK-信号弱, DATA_ABNORMAL-数据异常, CALIBRATION_EXPIRED-校验过期',
    fault_level VARCHAR(16) NOT NULL COMMENT '故障级别: LOW-低, MEDIUM-中, HIGH-高, CRITICAL-紧急',
    fault_description VARCHAR(512) COMMENT '故障描述',
    fault_time DATETIME NOT NULL COMMENT '故障发生时间',
    location VARCHAR(256) COMMENT '设备位置',
    zone_code VARCHAR(32) COMMENT '区域编码',
    assignee VARCHAR(64) COMMENT '指派维修人员',
    assignee_phone VARCHAR(32) COMMENT '维修人员电话',
    assignee_user_id VARCHAR(64) COMMENT '维修人员企微UserID',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待处理, 1-处理中, 2-已完成, 3-已关闭',
    resolution VARCHAR(512) COMMENT '处理结果',
    resolution_time DATETIME COMMENT '处理完成时间',
    resolved_by VARCHAR(64) COMMENT '处理人',
    notify_channels VARCHAR(256) COMMENT '通知渠道: SMS,EMAIL,APP,WEBHOOK',
    notify_status TINYINT DEFAULT 0 COMMENT '通知状态: 0-未通知, 1-已通知, 2-通知失败',
    notify_time DATETIME COMMENT '通知时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no),
    INDEX idx_sensor_id (sensor_id),
    INDEX idx_fault_type (fault_type),
    INDEX idx_fault_level (fault_level),
    INDEX idx_status (status),
    INDEX idx_assignee (assignee),
    INDEX idx_fault_time (fault_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备故障工单表';

INSERT IGNORE INTO sensor_comm_params (sensor_id, param_key, param_value, param_type, description) VALUES
('GAS-001', 'mqtt_topic', 'mine/sensor/data/GAS-001', 'STRING', '数据上报MQTT主题'),
('GAS-001', 'heartbeat_interval', '30', 'NUMBER', '心跳间隔(秒)'),
('GAS-001', 'retry_count', '3', 'NUMBER', '重连重试次数'),
('GAS-001', 'data_compress', 'false', 'STRING', '是否启用数据压缩'),
('GAS-002', 'mqtt_topic', 'mine/sensor/data/GAS-002', 'STRING', '数据上报MQTT主题'),
('GAS-002', 'heartbeat_interval', '30', 'NUMBER', '心跳间隔(秒)'),
('DUST-001', 'mqtt_topic', 'mine/sensor/data/DUST-001', 'STRING', '数据上报MQTT主题'),
('DUST-001', 'heartbeat_interval', '60', 'NUMBER', '心跳间隔(秒)'),
('CO-001', 'mqtt_topic', 'mine/sensor/data/CO-001', 'STRING', '数据上报MQTT主题'),
('CO-001', 'heartbeat_interval', '30', 'NUMBER', '心跳间隔(秒)');

INSERT IGNORE INTO sensor_device_shadow (sensor_id, reported_state, desired_state, reported_version, desired_version, sync_status) VALUES
('GAS-001', '{"samplingInterval":1,"warningThreshold":0.8,"alarmThreshold":1.0,"batteryLevel":100,"signalStrength":85}', '{"samplingInterval":1,"warningThreshold":0.8,"alarmThreshold":1.0}', 1, 1, 'SYNCED'),
('GAS-002', '{"samplingInterval":1,"warningThreshold":0.8,"alarmThreshold":1.0,"batteryLevel":95,"signalStrength":80}', '{"samplingInterval":1,"warningThreshold":0.8,"alarmThreshold":1.0}', 1, 1, 'SYNCED'),
('DUST-001', '{"samplingInterval":5,"warningThreshold":200,"alarmThreshold":500,"batteryLevel":88,"signalStrength":70}', '{"samplingInterval":5,"warningThreshold":200,"alarmThreshold":500}', 1, 1, 'SYNCED'),
('CO-001', '{"samplingInterval":2,"warningThreshold":24,"alarmThreshold":50,"batteryLevel":92,"signalStrength":75}', '{"samplingInterval":2,"warningThreshold":24,"alarmThreshold":50}', 1, 1, 'SYNCED');

CREATE TABLE IF NOT EXISTS maintenance_assignee_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_code VARCHAR(32) COMMENT '区域编码(NULL=全部)',
    sensor_type VARCHAR(32) COMMENT '传感器类型(NULL=全部)',
    fault_type VARCHAR(32) COMMENT '故障类型(NULL=全部)',
    assignee VARCHAR(64) NOT NULL COMMENT '维修人员姓名',
    assignee_phone VARCHAR(32) COMMENT '维修人员电话',
    assignee_user_id VARCHAR(64) COMMENT '维修人员企微UserID',
    priority INT DEFAULT 0 COMMENT '优先级(越大越优先)',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_zone_code (zone_code),
    INDEX idx_sensor_type (sensor_type),
    INDEX idx_fault_type (fault_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修人员指派规则表';

INSERT IGNORE INTO maintenance_assignee_rules (zone_code, sensor_type, fault_type, assignee, assignee_phone, assignee_user_id, priority, enabled) VALUES
('ZONE-01', 'GAS', 'OFFLINE', '张工', '13800000001', 'ZhangGong', 10, 1),
('ZONE-01', 'GAS', NULL, '李工', '13800000002', 'LiGong', 5, 1),
('ZONE-01', NULL, NULL, '王主管', '13800000003', 'WangZhuGuan', 1, 1),
('ZONE-02', 'DUST', NULL, '赵工', '13800000004', 'ZhaoGong', 5, 1),
('ZONE-02', NULL, NULL, '钱主管', '13800000005', 'QianZhuGuan', 1, 1),
(NULL, 'CO', 'OFFLINE', '孙工', '13800000006', 'SunGong', 5, 1),
(NULL, NULL, 'LOW_BATTERY', '周工', '13800000007', 'ZhouGong', 3, 1),
(NULL, NULL, NULL, '值班室', '13800000000', 'DutyRoom', 0, 1);
