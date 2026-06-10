CREATE DATABASE IF NOT EXISTS mine_safety DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mine_safety;

CREATE TABLE IF NOT EXISTS sensors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id VARCHAR(64) NOT NULL UNIQUE COMMENT '传感器唯一标识',
    name VARCHAR(128) NOT NULL COMMENT '传感器名称',
    type VARCHAR(32) NOT NULL COMMENT '传感器类型: GAS,DUST,CO,TEMPERATURE,WIND',
    protocol VARCHAR(32) NOT NULL COMMENT '通讯协议: MODBUS_RTU,MODBUS_TCP,OPC_UA,CAN,4G,5G',
    location VARCHAR(256) COMMENT '安装位置: 巷道/工作面',
    coordinates_x DECIMAL(10,6) COMMENT 'X坐标',
    coordinates_y DECIMAL(10,6) COMMENT 'Y坐标',
    coordinates_z DECIMAL(10,6) COMMENT 'Z坐标',
    sampling_interval INT DEFAULT 1 COMMENT '采样间隔(秒)',
    min_value DECIMAL(10,4) COMMENT '测量范围最小值',
    max_value DECIMAL(10,4) COMMENT '测量范围最大值',
    unit VARCHAR(16) COMMENT '单位',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-离线, 1-在线, 2-故障',
    last_online_time DATETIME COMMENT '最后在线时间',
    battery_level TINYINT DEFAULT 100 COMMENT '电量百分比: 0-100',
    signal_strength TINYINT DEFAULT 0 COMMENT '信号强度: 0-100',
    data_upload_delay INT DEFAULT 0 COMMENT '数据上报延迟(ms)',
    offline_timeout_minutes INT DEFAULT 10 COMMENT '离线超时时间(分钟)',
    calibration_cycle_days INT DEFAULT 90 COMMENT '校验周期(天)',
    last_calibration_date DATE COMMENT '最后校验日期',
    next_calibration_date DATE COMMENT '下次校验日期',
    warning_threshold DECIMAL(10,4) COMMENT '预警阈值',
    alarm_threshold DECIMAL(10,4) COMMENT '报警阈值',
    power_off_threshold DECIMAL(10,4) COMMENT '断电阈值',
    zone_code VARCHAR(32) COMMENT '区域编码',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sensor_id (sensor_id),
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_zone_code (zone_code),
    INDEX idx_battery_level (battery_level),
    INDEX idx_signal_strength (signal_strength)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器配置表';

CREATE TABLE IF NOT EXISTS sensor_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id VARCHAR(64) NOT NULL COMMENT '传感器ID',
    value DECIMAL(12,4) NOT NULL COMMENT '采集值',
    timestamp DATETIME NOT NULL COMMENT '采集时间',
    location VARCHAR(256) COMMENT '位置',
    quality TINYINT DEFAULT 1 COMMENT '数据质量: 0-异常, 1-正常',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sensor_id (sensor_id),
    INDEX idx_timestamp (timestamp),
    INDEX idx_sensor_time (sensor_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器历史数据表';

CREATE TABLE IF NOT EXISTS alert_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    sensor_type VARCHAR(32) COMMENT '传感器类型，空表示所有类型',
    sensor_id VARCHAR(64) COMMENT '指定传感器ID，空表示所有传感器',
    condition_type VARCHAR(32) NOT NULL COMMENT '条件类型: GT, GTE, LT, LTE, BETWEEN',
    threshold_value DECIMAL(12,4) COMMENT '阈值',
    threshold_value_max DECIMAL(12,4) COMMENT '上限阈值（用于BETWEEN）',
    duration INT DEFAULT 0 COMMENT '持续时间(秒)',
    level VARCHAR(16) NOT NULL COMMENT '报警级别: INFO, WARNING, ALERT, EMERGENCY',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    notification_channels VARCHAR(512) COMMENT '通知渠道: SMS,EMAIL,VOICE,WEBHOOK',
    description VARCHAR(512) COMMENT '规则描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sensor_type (sensor_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警规则表';

CREATE TABLE IF NOT EXISTS alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_no VARCHAR(64) NOT NULL UNIQUE COMMENT '报警编号',
    sensor_id VARCHAR(64) NOT NULL COMMENT '传感器ID',
    sensor_name VARCHAR(128) COMMENT '传感器名称',
    sensor_type VARCHAR(32) COMMENT '传感器类型',
    location VARCHAR(256) COMMENT '位置',
    alert_value DECIMAL(12,4) NOT NULL COMMENT '报警值',
    threshold_value DECIMAL(12,4) COMMENT '阈值',
    level VARCHAR(16) NOT NULL COMMENT '报警级别',
    rule_id BIGINT COMMENT '关联规则ID',
    rule_name VARCHAR(128) COMMENT '规则名称',
    description VARCHAR(512) COMMENT '报警描述',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-未处理, 1-处理中, 2-已处理, 3-已忽略',
    acknowledged_by VARCHAR(64) COMMENT '确认人',
    acknowledged_at DATETIME COMMENT '确认时间',
    acknowledged_comment VARCHAR(512) COMMENT '处理备注',
    first_alert_time DATETIME NOT NULL COMMENT '首次报警时间',
    last_alert_time DATETIME NOT NULL COMMENT '最后报警时间',
    alert_count INT DEFAULT 1 COMMENT '报警次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_alert_no (alert_no),
    INDEX idx_sensor_id (sensor_id),
    INDEX idx_status (status),
    INDEX idx_level (level),
    INDEX idx_alert_time (first_alert_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警记录表';

CREATE TABLE IF NOT EXISTS alert_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_id BIGINT NOT NULL COMMENT '报警ID',
    channel VARCHAR(32) NOT NULL COMMENT '通知渠道: SMS,EMAIL,VOICE,WEBHOOK',
    recipient VARCHAR(256) NOT NULL COMMENT '接收人',
    content TEXT COMMENT '通知内容',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待发送, 1-已发送, 2-发送失败',
    send_time DATETIME COMMENT '发送时间',
    error_msg VARCHAR(512) COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_alert_id (alert_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警通知表';

CREATE TABLE IF NOT EXISTS work_zones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_code VARCHAR(32) NOT NULL UNIQUE COMMENT '区域编码',
    zone_name VARCHAR(128) NOT NULL COMMENT '区域名称',
    zone_type VARCHAR(32) COMMENT '区域类型: 采煤工作面,掘进工作面,回风巷,运输巷,机电硐室',
    parent_zone_code VARCHAR(32) COMMENT '上级区域编码',
    coordinates_x DECIMAL(10,6) COMMENT '区域中心X坐标',
    coordinates_y DECIMAL(10,6) COMMENT '区域中心Y坐标',
    coordinates_z DECIMAL(10,6) COMMENT '区域中心Z坐标',
    description VARCHAR(512) COMMENT '区域描述',
    enabled TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_zone_code (zone_code),
    INDEX idx_parent_zone (parent_zone_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业区域表';

CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(64) NOT NULL UNIQUE,
    config_value TEXT,
    config_type VARCHAR(32) DEFAULT 'STRING',
    description VARCHAR(256),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

INSERT IGNORE INTO sensors (sensor_id, name, type, protocol, location, coordinates_x, coordinates_y, coordinates_z, sampling_interval, min_value, max_value, unit, warning_threshold, alarm_threshold, power_off_threshold, zone_code) VALUES
('GAS-001', '回风巷瓦斯传感器', 'GAS', 'MODBUS_RTU', '回风巷工作面A', 116.5, 39.8, 520.5, 1, 0, 4, '% CH4', 0.8, 1.0, 1.5, 'ZONE-004'),
('GAS-002', '综采面瓦斯传感器', 'GAS', 'MODBUS_RTU', '综采工作面C', 116.51, 39.81, 518.3, 1, 0, 4, '% CH4', 0.8, 1.0, 1.5, 'ZONE-002'),
('DUST-001', '掘进面粉尘传感器', 'DUST', 'MODBUS_RTU', '掘进工作面B', 116.49, 39.82, 525.1, 5, 0, 1000, 'mg/m³', 200, 500, 1000, 'ZONE-003'),
('DUST-002', '运输巷粉尘传感器', 'DUST', 'MODBUS_TCP', '主运输大巷', 116.48, 39.79, 530.0, 5, 0, 1000, 'mg/m³', 200, 500, 1000, 'ZONE-005'),
('CO-001', '主巷道CO传感器', 'CO', 'MODBUS_TCP', '主运输大巷', 116.48, 39.79, 530.0, 2, 0, 500, 'ppm', 24, 50, 100, 'ZONE-005'),
('CO-002', '机电硐室CO传感器', 'CO', 'OPC_UA', '机电硐室', 116.47, 39.78, 535.5, 2, 0, 500, 'ppm', 24, 50, 100, 'ZONE-006'),
('TEMP-001', '综采面温度传感器', 'TEMPERATURE', 'MODBUS_TCP', '综采工作面C', 116.51, 39.81, 518.3, 5, -5, 100, '℃', 26, 30, 35, 'ZONE-002'),
('TEMP-002', '机电硐室温度传感器', 'TEMPERATURE', 'OPC_UA', '机电硐室', 116.47, 39.78, 535.5, 5, -5, 100, '℃', 30, 35, 40, 'ZONE-006'),
('WIND-001', '通风巷风速传感器', 'WIND', 'MODBUS_RTU', '回风上山', 116.52, 39.83, 515.0, 10, 0, 15, 'm/s', 0.5, 0.3, 0.2, 'ZONE-004'),
('WIND-002', '进风巷风速传感器', 'WIND', 'CAN', '进风大巷', 116.46, 39.77, 540.0, 10, 0, 15, 'm/s', 0.5, 0.3, 0.2, 'ZONE-005');

INSERT IGNORE INTO alert_rules (rule_name, sensor_type, condition_type, threshold_value, duration, level, enabled, notification_channels, description) VALUES
('瓦斯浓度预警', 'GAS', 'GTE', 0.8, 5, 'WARNING', 1, 'SMS,EMAIL,WEBHOOK', '瓦斯浓度超过0.8% CH4持续5秒触发预警'),
('瓦斯浓度报警', 'GAS', 'GTE', 1.0, 3, 'ALERT', 1, 'SMS,EMAIL,VOICE,WEBHOOK', '瓦斯浓度超过1.0% CH4持续3秒触发报警'),
('瓦斯浓度紧急', 'GAS', 'GTE', 2.0, 1, 'EMERGENCY', 1, 'SMS,EMAIL,VOICE,WEBHOOK', '瓦斯浓度超过2.0% CH4立即紧急报警'),
('粉尘浓度预警', 'DUST', 'GTE', 200, 10, 'WARNING', 1, 'SMS,EMAIL,WEBHOOK', '粉尘浓度超过200mg/m³持续10秒触发预警'),
('粉尘浓度报警', 'DUST', 'GTE', 500, 5, 'ALERT', 1, 'SMS,EMAIL,VOICE,WEBHOOK', '粉尘浓度超过500mg/m³持续5秒触发报警'),
('一氧化碳预警', 'CO', 'GTE', 24, 5, 'WARNING', 1, 'SMS,EMAIL,WEBHOOK', 'CO浓度超过24ppm持续5秒触发预警'),
('一氧化碳报警', 'CO', 'GTE', 50, 3, 'ALERT', 1, 'SMS,EMAIL,VOICE,WEBHOOK', 'CO浓度超过50ppm持续3秒触发报警'),
('温度预警', 'TEMPERATURE', 'GTE', 26, 30, 'WARNING', 1, 'EMAIL,WEBHOOK', '温度超过26℃持续30秒触发预警'),
('温度报警', 'TEMPERATURE', 'GTE', 30, 10, 'ALERT', 1, 'SMS,EMAIL,VOICE,WEBHOOK', '温度超过30℃持续10秒触发报警'),
('风速过低报警', 'WIND', 'LTE', 0.3, 30, 'ALERT', 1, 'SMS,EMAIL,WEBHOOK', '风速低于0.3m/s持续30秒触发报警');

INSERT IGNORE INTO work_zones (zone_code, zone_name, zone_type, parent_zone_code, coordinates_x, coordinates_y, coordinates_z, description) VALUES
('ZONE-001', '一号矿井', 'MINE', NULL, 116.5, 39.8, 520.0, '主矿井区域'),
('ZONE-002', '综采工作面A', '采煤工作面', 'ZONE-001', 116.51, 39.81, 518.0, '综合机械化采煤工作面'),
('ZONE-003', '掘进工作面B', '掘进工作面', 'ZONE-001', 116.49, 39.82, 525.0, '巷道掘进工作面'),
('ZONE-004', '回风巷', '回风巷', 'ZONE-001', 116.52, 39.83, 515.0, '回风巷道'),
('ZONE-005', '主运输大巷', '运输巷', 'ZONE-001', 116.48, 39.79, 530.0, '主运输巷道'),
('ZONE-006', '机电硐室', '机电硐室', 'ZONE-001', 116.47, 39.78, 535.5, '机电设备硐室');

CREATE TABLE IF NOT EXISTS threshold_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id VARCHAR(64) NOT NULL COMMENT '传感器ID',
    threshold_type VARCHAR(32) NOT NULL COMMENT '阈值类型: WARNING, ALARM, POWER_OFF',
    old_value DECIMAL(10,4) COMMENT '原值',
    new_value DECIMAL(10,4) NOT NULL COMMENT '新值',
    operator VARCHAR(64) NOT NULL COMMENT '操作人',
    operation_type VARCHAR(32) NOT NULL COMMENT '操作类型: CREATE, UPDATE, APPROVE, REJECT',
    approval_id BIGINT COMMENT '关联审批ID',
    change_reason VARCHAR(512) COMMENT '变更原因',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sensor_id (sensor_id),
    INDEX idx_threshold_type (threshold_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阈值变更审计表';

CREATE TABLE IF NOT EXISTS threshold_approval (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_no VARCHAR(64) NOT NULL UNIQUE COMMENT '审批编号',
    sensor_id VARCHAR(64) NOT NULL COMMENT '传感器ID',
    threshold_type VARCHAR(32) NOT NULL COMMENT '阈值类型: WARNING, ALARM, POWER_OFF',
    old_value DECIMAL(10,4) COMMENT '原值',
    new_value DECIMAL(10,4) NOT NULL COMMENT '新值',
    applicant VARCHAR(64) NOT NULL COMMENT '申请人',
    apply_reason VARCHAR(512) COMMENT '申请原因',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待审批, 1-已通过, 2-已拒绝',
    approver VARCHAR(64) COMMENT '审批人',
    approve_comment VARCHAR(512) COMMENT '审批意见',
    approved_at DATETIME COMMENT '审批时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_approval_no (approval_no),
    INDEX idx_sensor_id (sensor_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阈值调整审批表';

INSERT IGNORE INTO system_config (config_key, config_value, config_type, description) VALUES
('mqtt.broker.url', 'tcp://localhost:1883', 'STRING', 'MQTT Broker地址'),
('mqtt.topic.sensor.data', 'mine/sensor/data', 'STRING', '传感器数据主题'),
('mqtt.topic.alarm', 'mine/alarm/#', 'STRING', '报警主题'),
('kafka.bootstrap.servers', 'localhost:29092', 'STRING', 'Kafka地址'),
('kafka.topic.sensor.raw', 'sensor-raw-data', 'STRING', '原始数据Topic'),
('kafka.topic.sensor.processed', 'sensor-processed-data', 'STRING', '处理后数据Topic'),
('kafka.topic.alarm', 'alarm-events', 'STRING', '报警事件Topic'),
('influxdb.url', 'http://localhost:8086', 'STRING', 'InfluxDB地址'),
('influxdb.token', 'mine-safety-token-2024', 'STRING', 'InfluxDB Token'),
('influxdb.org', 'mine-safety', 'STRING', 'InfluxDB组织'),
('influxdb.bucket', 'sensor-data', 'STRING', 'InfluxDB Bucket'),
('data.cleanup.retention.days', '30', 'NUMBER', '数据保留天数');

-- ==================== 报警规则引擎表 ====================

CREATE TABLE IF NOT EXISTS alert_rule_definitions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code VARCHAR(64) NOT NULL UNIQUE COMMENT '规则编码',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    rule_type VARCHAR(32) NOT NULL COMMENT '规则类型: SINGLE_THRESHOLD,TREND,COMPOUND',
    sensor_type VARCHAR(32) COMMENT '传感器类型',
    sensor_id VARCHAR(64) COMMENT '指定传感器ID',
    zone_code VARCHAR(32) COMMENT '区域编码',
    drools_rule TEXT COMMENT 'Drools规则内容',
    groovy_script TEXT COMMENT 'Groovy脚本内容',
    rule_params JSON COMMENT '规则参数(JSON)',
    level VARCHAR(16) NOT NULL COMMENT '报警级别: INFO,WARNING,ALERT,EMERGENCY',
    description VARCHAR(512) COMMENT '规则描述',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    version INT DEFAULT 1 COMMENT '版本号',
    created_by VARCHAR(64) COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) COMMENT '更新人',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_rule_code (rule_code),
    INDEX idx_rule_type (rule_type),
    INDEX idx_sensor_type (sensor_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警规则定义表';

-- ==================== 联动控制表 ====================

CREATE TABLE IF NOT EXISTS linkage_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action_code VARCHAR(64) NOT NULL UNIQUE COMMENT '动作编码',
    action_name VARCHAR(128) NOT NULL COMMENT '动作名称',
    action_type VARCHAR(32) NOT NULL COMMENT '动作类型: SOUND_LIGHT_ALARM,VOICE_BROADCAST,REMOTE_POWER_OFF,MESSAGE_PUSH,VIDEO_POPUP',
    target_type VARCHAR(32) COMMENT '目标类型: ZONE,SENSOR,DEVICE,USER',
    target_code VARCHAR(64) COMMENT '目标编码',
    action_params JSON COMMENT '动作参数(JSON)',
    execution_mode VARCHAR(16) DEFAULT 'PARALLEL' COMMENT '执行模式: SERIAL-串行, PARALLEL-并行',
    priority INT DEFAULT 0 COMMENT '优先级: 0-低, 1-中, 2-高',
    timeout_seconds INT DEFAULT 30 COMMENT '超时时间(秒)',
    max_retry INT DEFAULT 3 COMMENT '最大重试次数',
    retry_interval_seconds INT DEFAULT 5 COMMENT '重试间隔(秒)',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    description VARCHAR(512) COMMENT '动作描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_action_code (action_code),
    INDEX idx_action_type (action_type),
    INDEX idx_target_type (target_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联动动作定义表';

CREATE TABLE IF NOT EXISTS alert_rule_action_relations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT NOT NULL COMMENT '规则ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '规则编码',
    action_id BIGINT NOT NULL COMMENT '动作ID',
    action_code VARCHAR(64) NOT NULL COMMENT '动作编码',
    execution_order INT DEFAULT 0 COMMENT '执行顺序',
    delay_seconds INT DEFAULT 0 COMMENT '延迟执行(秒)',
    condition_expression VARCHAR(512) COMMENT '执行条件表达式',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rule_id (rule_id),
    INDEX idx_action_id (action_id),
    UNIQUE KEY uk_rule_action (rule_id, action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则动作关联表';

CREATE TABLE IF NOT EXISTS linkage_execution_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_no VARCHAR(64) NOT NULL UNIQUE COMMENT '执行编号',
    rule_id BIGINT COMMENT '触发规则ID',
    rule_code VARCHAR(64) COMMENT '触发规则编码',
    alert_id BIGINT COMMENT '关联报警ID',
    action_id BIGINT NOT NULL COMMENT '动作ID',
    action_code VARCHAR(64) NOT NULL COMMENT '动作编码',
    action_type VARCHAR(32) NOT NULL COMMENT '动作类型',
    target_type VARCHAR(32) COMMENT '目标类型',
    target_code VARCHAR(64) COMMENT '目标编码',
    action_params JSON COMMENT '执行时参数',
    request_payload TEXT COMMENT '请求报文',
    response_payload TEXT COMMENT '响应报文',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待执行, 1-执行中, 2-成功, 3-失败, 4-超时, 5-已取消',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    execution_start_time DATETIME COMMENT '执行开始时间',
    execution_end_time DATETIME COMMENT '执行结束时间',
    duration_ms BIGINT COMMENT '执行耗时(毫秒)',
    error_msg VARCHAR(1024) COMMENT '错误信息',
    operator VARCHAR(64) COMMENT '触发人: SYSTEM/USERNAME',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_execution_no (execution_no),
    INDEX idx_alert_id (alert_id),
    INDEX idx_rule_id (rule_id),
    INDEX idx_action_id (action_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联动执行记录表';

-- ==================== PLC设备表 ====================

CREATE TABLE IF NOT EXISTS plc_devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_code VARCHAR(64) NOT NULL UNIQUE COMMENT '设备编码',
    device_name VARCHAR(128) NOT NULL COMMENT '设备名称',
    device_type VARCHAR(32) NOT NULL COMMENT '设备类型: PLC_SOUND_LIGHT,PLC_BROADCAST,PLC_POWER_CONTROL,PLC_OTHER',
    protocol VARCHAR(32) NOT NULL COMMENT '通讯协议: MODBUS_TCP,OPC_UA,S7,MODBUS_RTU',
    ip_address VARCHAR(64) COMMENT 'IP地址',
    port INT COMMENT '端口号',
    slave_id INT DEFAULT 1 COMMENT '从站地址',
    rack INT DEFAULT 0 COMMENT '机架号(S7)',
    slot INT DEFAULT 1 COMMENT '槽号(S7)',
    register_address VARCHAR(64) COMMENT '寄存器地址',
    register_type VARCHAR(32) COMMENT '寄存器类型: COIL,HOLDING_REGISTER,INPUT_REGISTER',
    data_type VARCHAR(32) DEFAULT 'BOOL' COMMENT '数据类型: BOOL,INT16,INT32,FLOAT',
    zone_code VARCHAR(32) COMMENT '所属区域',
    location VARCHAR(256) COMMENT '安装位置',
    on_value VARCHAR(32) DEFAULT '1' COMMENT '开启值',
    off_value VARCHAR(32) DEFAULT '0' COMMENT '关闭值',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-离线, 1-在线, 2-故障',
    last_online_time DATETIME COMMENT '最后在线时间',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    description VARCHAR(512) COMMENT '设备描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_device_code (device_code),
    INDEX idx_device_type (device_type),
    INDEX idx_protocol (protocol),
    INDEX idx_zone_code (zone_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PLC设备表';

-- ==================== 消息推送配置表 ====================

CREATE TABLE IF NOT EXISTS message_push_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_code VARCHAR(64) NOT NULL UNIQUE COMMENT '配置编码',
    config_name VARCHAR(128) NOT NULL COMMENT '配置名称',
    push_channel VARCHAR(32) NOT NULL COMMENT '推送渠道: WECHAT_WORK,FCM,SMS,EMAIL,APP',
    channel_params JSON COMMENT '渠道参数(JSON)',
    target_users VARCHAR(1024) COMMENT '目标用户列表(逗号分隔)',
    target_roles VARCHAR(512) COMMENT '目标角色列表(逗号分隔)',
    template_code VARCHAR(64) COMMENT '消息模板编码',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_config_code (config_code),
    INDEX idx_push_channel (push_channel),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息推送配置表';

-- ==================== 初始化数据 ====================

INSERT IGNORE INTO alert_rule_definitions (rule_code, rule_name, rule_type, sensor_type, level, description, rule_params, enabled) VALUES
('RULE-SINGLE-GAS-WARNING', '瓦斯浓度单阈值预警', 'SINGLE_THRESHOLD', 'GAS', 'WARNING', '瓦斯浓度超过0.8%预警', '{"threshold":0.8,"operator":"GTE","duration":5}', 1),
('RULE-SINGLE-GAS-ALARM', '瓦斯浓度单阈值报警', 'SINGLE_THRESHOLD', 'GAS', 'ALERT', '瓦斯浓度超过1.0%报警', '{"threshold":1.0,"operator":"GTE","duration":3}', 1),
('RULE-TREND-GAS-RISE', '瓦斯浓度趋势报警', 'TREND', 'GAS', 'ALERT', '瓦斯浓度10秒内上升速度>0.2%/s', '{"windowSeconds":10,"riseRate":0.2,"unit":"%/s"}', 1),
('RULE-COMPOUND-GAS-TEMP', '瓦斯+温度复合报警', 'COMPOUND', NULL, 'EMERGENCY', '瓦斯和温度同时超标', '{"conditions":[{"sensorType":"GAS","operator":"GTE","threshold":1.0},{"sensorType":"TEMPERATURE","operator":"GTE","threshold":30}]}', 1),
('RULE-SINGLE-DUST-WARNING', '粉尘浓度单阈值预警', 'SINGLE_THRESHOLD', 'DUST', 'WARNING', '粉尘浓度超过200mg/m³预警', '{"threshold":200,"operator":"GTE","duration":10}', 1),
('RULE-SINGLE-CO-ALARM', 'CO浓度单阈值报警', 'SINGLE_THRESHOLD', 'CO', 'ALERT', 'CO浓度超过50ppm报警', '{"threshold":50,"operator":"GTE","duration":3}', 1);

INSERT IGNORE INTO plc_devices (device_code, device_name, device_type, protocol, ip_address, port, slave_id, register_address, register_type, zone_code, location, description, enabled) VALUES
('PLC-SOUND-001', '综采面声光报警器', 'PLC_SOUND_LIGHT', 'MODBUS_TCP', '192.168.1.101', 502, 1, '00001', 'COIL', 'ZONE-002', '综采工作面A', '综采工作面声光报警设备', 1),
('PLC-SOUND-002', '掘进面声光报警器', 'PLC_SOUND_LIGHT', 'MODBUS_TCP', '192.168.1.102', 502, 1, '00001', 'COIL', 'ZONE-003', '掘进工作面B', '掘进工作面声光报警设备', 1),
('PLC-SOUND-003', '回风巷声光报警器', 'PLC_SOUND_LIGHT', 'MODBUS_TCP', '192.168.1.103', 502, 1, '00001', 'COIL', 'ZONE-004', '回风巷', '回风巷声光报警设备', 1),
('PLC-BROADCAST-001', '主巷道语音广播', 'PLC_BROADCAST', 'MODBUS_TCP', '192.168.1.111', 502, 1, '00002', 'COIL', 'ZONE-005', '主运输大巷', '主巷道语音广播系统', 1),
('PLC-POWER-001', '综采面供电控制', 'PLC_POWER_CONTROL', 'MODBUS_TCP', '192.168.1.121', 502, 1, '00010', 'COIL', 'ZONE-002', '综采工作面A', '综采工作面远程断电控制', 1),
('PLC-POWER-002', '掘进面供电控制', 'PLC_POWER_CONTROL', 'MODBUS_TCP', '192.168.1.122', 502, 1, '00010', 'COIL', 'ZONE-003', '掘进工作面B', '掘进工作面远程断电控制', 1),
('PLC-POWER-003', '机电硐室供电控制', 'PLC_POWER_CONTROL', 'MODBUS_TCP', '192.168.1.123', 502, 1, '00010', 'COIL', 'ZONE-006', '机电硐室', '机电硐室远程断电控制', 1);

INSERT IGNORE INTO linkage_actions (action_code, action_name, action_type, target_type, target_code, action_params, execution_mode, priority, timeout_seconds, max_retry, retry_interval_seconds, description, enabled) VALUES
('ACTION-SOUND-ALL', '全矿井声光报警', 'SOUND_LIGHT_ALARM', 'ZONE', 'ZONE-001', '{"pattern":"ALARM_EMERGENCY","duration":300}', 'PARALLEL', 2, 30, 3, 5, '触发全矿井声光报警器', 1),
('ACTION-SOUND-ZONE', '区域声光报警', 'SOUND_LIGHT_ALARM', 'ZONE', NULL, '{"pattern":"ALARM_WARNING","duration":120}', 'PARALLEL', 1, 30, 2, 5, '触发指定区域声光报警器', 1),
('ACTION-BROADCAST-EVACUATE', '语音广播-撤离指令', 'VOICE_BROADCAST', 'ZONE', NULL, '{"audioFile":"evacuate_instruction.mp3","loop":3,"volume":100}', 'PARALLEL', 2, 60, 3, 10, '向指定区域广播语音撤离指令', 1),
('ACTION-BROADCAST-WARNING', '语音广播-预警通知', 'VOICE_BROADCAST', 'ZONE', NULL, '{"audioFile":"warning_notice.mp3","loop":2,"volume":80}', 'PARALLEL', 1, 60, 2, 10, '向指定区域广播预警通知', 1),
('ACTION-POWER-OFF-ZONE', '区域远程断电', 'REMOTE_POWER_OFF', 'ZONE', NULL, '{"confirm":true,"reason":"报警联动断电"}', 'SERIAL', 2, 120, 3, 30, '切断指定区域非安全电源', 1),
('ACTION-POWER-OFF-SENSOR', '传感器关联设备断电', 'REMOTE_POWER_OFF', 'SENSOR', NULL, '{"confirm":true,"reason":"报警联动断电"}', 'SERIAL', 2, 120, 3, 30, '切断传感器关联设备电源', 1),
('ACTION-PUSH-SAFETY', '推送安全员手环/APP', 'MESSAGE_PUSH', 'ROLE', 'SAFETY_OFFICER', '{"channels":["APP","WECHAT_WORK"],"urgent":true}', 'PARALLEL', 1, 30, 2, 5, '推送报警信息至安全员手环和手机APP', 1),
('ACTION-PUSH-MANAGER', '推送管理人员', 'MESSAGE_PUSH', 'ROLE', 'MINE_MANAGER', '{"channels":["WECHAT_WORK","SMS"],"urgent":true}', 'PARALLEL', 1, 30, 2, 5, '推送报警信息至管理人员', 1),
('ACTION-VIDEO-POPUP', '视频监控弹出', 'VIDEO_POPUP', 'ZONE', NULL, '{"monitorCodes":[],"popupDuration":60,"layout":"4x4"}', 'PARALLEL', 0, 15, 1, 5, '监控中心弹出对应区域视频画面', 1);

INSERT IGNORE INTO alert_rule_action_relations (rule_id, rule_code, action_id, action_code, execution_order, delay_seconds) VALUES
(1, 'RULE-SINGLE-GAS-WARNING', 2, 'ACTION-SOUND-ZONE', 1, 0),
(1, 'RULE-SINGLE-GAS-WARNING', 4, 'ACTION-BROADCAST-WARNING', 2, 0),
(1, 'RULE-SINGLE-GAS-WARNING', 7, 'ACTION-PUSH-SAFETY', 3, 0),
(2, 'RULE-SINGLE-GAS-ALARM', 2, 'ACTION-SOUND-ZONE', 1, 0),
(2, 'RULE-SINGLE-GAS-ALARM', 3, 'ACTION-BROADCAST-EVACUATE', 2, 0),
(2, 'RULE-SINGLE-GAS-ALARM', 5, 'ACTION-POWER-OFF-ZONE', 3, 5),
(2, 'RULE-SINGLE-GAS-ALARM', 7, 'ACTION-PUSH-SAFETY', 4, 0),
(2, 'RULE-SINGLE-GAS-ALARM', 8, 'ACTION-PUSH-MANAGER', 5, 0),
(2, 'RULE-SINGLE-GAS-ALARM', 9, 'ACTION-VIDEO-POPUP', 6, 0),
(3, 'RULE-TREND-GAS-RISE', 2, 'ACTION-SOUND-ZONE', 1, 0),
(3, 'RULE-TREND-GAS-RISE', 3, 'ACTION-BROADCAST-EVACUATE', 2, 0),
(3, 'RULE-TREND-GAS-RISE', 7, 'ACTION-PUSH-SAFETY', 3, 0),
(4, 'RULE-COMPOUND-GAS-TEMP', 1, 'ACTION-SOUND-ALL', 1, 0),
(4, 'RULE-COMPOUND-GAS-TEMP', 3, 'ACTION-BROADCAST-EVACUATE', 2, 0),
(4, 'RULE-COMPOUND-GAS-TEMP', 5, 'ACTION-POWER-OFF-ZONE', 3, 3),
(4, 'RULE-COMPOUND-GAS-TEMP', 7, 'ACTION-PUSH-SAFETY', 4, 0),
(4, 'RULE-COMPOUND-GAS-TEMP', 8, 'ACTION-PUSH-MANAGER', 5, 0),
(4, 'RULE-COMPOUND-GAS-TEMP', 9, 'ACTION-VIDEO-POPUP', 6, 0);

INSERT IGNORE INTO message_push_configs (config_code, config_name, push_channel, channel_params, target_roles, enabled) VALUES
('PUSH-WECHAT-WORK', '企业微信推送', 'WECHAT_WORK', '{"corpId":"wwxxxxxxxxxxxxxx","corpSecret":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx","agentId":1000001}', 'SAFETY_OFFICER,MINE_MANAGER,SHIFT_SUPERVISOR', 1),
('PUSH-FCM-APP', '手机APP推送', 'FCM', '{"serviceAccountKey":"xxxxxxxxxx","projectId":"mine-safety-app"}', 'SAFETY_OFFICER,MINE_MANAGER,SHIFT_SUPERVISOR', 1),
('PUSH-SMS', '短信推送', 'SMS', '{"provider":"aliyun","accessKey":"xxxxxxxxxx","secretKey":"xxxxxxxxxx","signName":"煤矿安全平台"}', 'MINE_MANAGER,SAFETY_DIRECTOR', 1);

-- ==================== 传感器设备与通信管理 ====================

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

-- ==================== 报表与趋势分析 ====================

CREATE TABLE IF NOT EXISTS report_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(64) NOT NULL UNIQUE COMMENT '模板编码',
    template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
    template_type VARCHAR(32) NOT NULL COMMENT '模板类型: DAILY_GAS-瓦斯日报, WEEKLY_DUST-粉尘周报, MONTHLY_SUMMARY-月度汇总',
    description VARCHAR(512) COMMENT '模板描述',
    sensor_types VARCHAR(256) NOT NULL COMMENT '关联传感器类型(逗号分隔)',
    time_dimension VARCHAR(16) NOT NULL COMMENT '时间维度: HOUR,DAY,WEEK,MONTH',
    content_template JSON COMMENT '报表内容模板(JSON)',
    file_format VARCHAR(16) DEFAULT 'PDF' COMMENT '默认导出格式: PDF,EXCEL',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_template_type (template_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表模板表';

CREATE TABLE IF NOT EXISTS report_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_no VARCHAR(64) NOT NULL UNIQUE COMMENT '报表编号',
    template_id BIGINT NOT NULL COMMENT '关联模板ID',
    template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
    report_name VARCHAR(256) NOT NULL COMMENT '报表名称',
    report_type VARCHAR(32) NOT NULL COMMENT '报表类型',
    start_date DATE NOT NULL COMMENT '统计开始日期',
    end_date DATE NOT NULL COMMENT '统计结束日期',
    time_dimension VARCHAR(16) NOT NULL COMMENT '时间维度',
    sensor_types VARCHAR(256) COMMENT '包含传感器类型',
    zone_code VARCHAR(32) COMMENT '区域编码',
    report_data JSON COMMENT '报表数据(JSON)',
    file_format VARCHAR(16) DEFAULT 'PDF' COMMENT '文件格式: PDF,EXCEL',
    file_path VARCHAR(512) COMMENT 'MinIO文件路径',
    file_size BIGINT COMMENT '文件大小(字节)',
    file_url VARCHAR(1024) COMMENT '文件访问URL',
    generated_by VARCHAR(64) COMMENT '生成人',
    generation_source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '生成来源: MANUAL-手动, SCHEDULED-定时',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-生成中, 1-已完成, 2-生成失败',
    error_message VARCHAR(512) COMMENT '错误信息',
    email_sent TINYINT DEFAULT 0 COMMENT '是否已邮件推送: 0-否, 1-是',
    email_sent_time DATETIME COMMENT '邮件推送时间',
    email_recipients VARCHAR(1024) COMMENT '邮件接收人(逗号分隔)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_report_no (report_no),
    INDEX idx_template_id (template_id),
    INDEX idx_report_type (report_type),
    INDEX idx_start_date (start_date),
    INDEX idx_end_date (end_date),
    INDEX idx_status (status),
    INDEX idx_generation_source (generation_source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表记录表';

CREATE TABLE IF NOT EXISTS trend_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code VARCHAR(64) NOT NULL UNIQUE COMMENT '规则编码',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    description VARCHAR(512) COMMENT '规则描述',
    sensor_type VARCHAR(32) NOT NULL COMMENT '传感器类型',
    zone_code VARCHAR(32) COMMENT '区域编码(NULL=全部)',
    metric VARCHAR(32) NOT NULL COMMENT '监测指标: DAILY_AVG-日均值, DAILY_MAX-日最大值, OVER_THRESHOLD_COUNT-超标次数',
    trend_direction VARCHAR(16) NOT NULL COMMENT '趋势方向: RISING-上升, FALLING-下降',
    consecutive_periods INT NOT NULL DEFAULT 3 COMMENT '连续周期数',
    period_unit VARCHAR(16) NOT NULL DEFAULT 'WEEK' COMMENT '周期单位: DAY,WEEK,MONTH',
    threshold_value DECIMAL(10,4) COMMENT '触发阈值(可选)',
    severity VARCHAR(16) NOT NULL DEFAULT 'WARNING' COMMENT '严重级别: INFO,WARNING,ALERT,CRITICAL',
    notification_channels VARCHAR(256) DEFAULT 'APP,WECHAT_WORK' COMMENT '通知渠道',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sensor_type (sensor_type),
    INDEX idx_zone_code (zone_code),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='趋势分析规则表';

CREATE TABLE IF NOT EXISTS trend_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_no VARCHAR(64) NOT NULL UNIQUE COMMENT '趋势预警编号',
    rule_id BIGINT NOT NULL COMMENT '关联规则ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '规则编码',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    sensor_type VARCHAR(32) NOT NULL COMMENT '传感器类型',
    zone_code VARCHAR(32) COMMENT '区域编码',
    metric VARCHAR(32) NOT NULL COMMENT '监测指标',
    trend_direction VARCHAR(16) NOT NULL COMMENT '趋势方向',
    consecutive_periods INT NOT NULL COMMENT '连续周期数',
    period_unit VARCHAR(16) NOT NULL COMMENT '周期单位',
    start_date DATE NOT NULL COMMENT '趋势开始日期',
    end_date DATE NOT NULL COMMENT '趋势结束日期',
    trend_data JSON COMMENT '趋势数据(JSON, 每周期值)',
    description VARCHAR(1024) COMMENT '趋势描述',
    severity VARCHAR(16) NOT NULL COMMENT '严重级别',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待处理, 1-已确认, 2-已忽略',
    acknowledged_by VARCHAR(64) COMMENT '确认人',
    acknowledged_at DATETIME COMMENT '确认时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_alert_no (alert_no),
    INDEX idx_rule_id (rule_id),
    INDEX idx_sensor_type (sensor_type),
    INDEX idx_zone_code (zone_code),
    INDEX idx_severity (severity),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='趋势预警记录表';

INSERT IGNORE INTO report_templates (template_code, template_name, template_type, description, sensor_types, time_dimension, content_template, file_format, enabled) VALUES
('GAS_DAILY', '煤矿瓦斯监测日报表', 'DAILY_GAS', '每日瓦斯浓度监测数据汇总报表', 'GAS', 'DAY',
 '{"sections":["header","sensor_summary","hourly_data","alert_summary","conclusion"]}', 'PDF', 1),
('DUST_WEEKLY', '粉尘浓度周报', 'WEEKLY_DUST', '每周粉尘浓度监测数据汇总报表', 'DUST', 'WEEK',
 '{"sections":["header","daily_summary","alert_summary","conclusion"]}', 'EXCEL', 1),
('MONTHLY_SUMMARY', '月度安全监测汇总表', 'MONTHLY_SUMMARY', '月度全类型传感器监测数据汇总报表', 'GAS,DUST,CO', 'MONTH',
 '{"sections":["header","sensor_type_summary","alert_summary","trend_analysis","conclusion"]}', 'PDF', 1);

INSERT IGNORE INTO trend_rules (rule_code, rule_name, description, sensor_type, zone_code, metric, trend_direction, consecutive_periods, period_unit, severity, notification_channels, enabled) VALUES
('GAS_RISING_3W', '瓦斯浓度三周连续上升', '某巷道瓦斯浓度日均值连续三周上升，提示风险评估', 'GAS', NULL, 'DAILY_AVG', 'RISING', 3, 'WEEK', 'WARNING', 'APP,WECHAT_WORK', 1),
('DUST_RISING_2W', '粉尘浓度两周连续上升', '某区域粉尘浓度日均值连续两周上升', 'DUST', NULL, 'DAILY_AVG', 'RISING', 2, 'WEEK', 'INFO', 'APP', 1),
('CO_RISING_3D', 'CO浓度三日连续上升', '某区域CO浓度日均值连续三日上升，可能存在自燃风险', 'CO', NULL, 'DAILY_AVG', 'RISING', 3, 'DAY', 'ALERT', 'APP,WECHAT_WORK,SMS', 1),
('GAS_OVER_THRESHOLD_UP', '瓦斯超标次数连续上升', '瓦斯日超标次数连续两周上升', 'GAS', NULL, 'OVER_THRESHOLD_COUNT', 'RISING', 2, 'WEEK', 'WARNING', 'APP,WECHAT_WORK', 1);
