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
    warning_threshold DECIMAL(10,4) COMMENT '预警阈值',
    alarm_threshold DECIMAL(10,4) COMMENT '报警阈值',
    power_off_threshold DECIMAL(10,4) COMMENT '断电阈值',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sensor_id (sensor_id),
    INDEX idx_type (type),
    INDEX idx_status (status)
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

INSERT IGNORE INTO sensors (sensor_id, name, type, protocol, location, coordinates_x, coordinates_y, coordinates_z, sampling_interval, min_value, max_value, unit, warning_threshold, alarm_threshold, power_off_threshold) VALUES
('GAS-001', '回风巷瓦斯传感器', 'GAS', 'MODBUS_RTU', '回风巷工作面A', 116.5, 39.8, 520.5, 1, 0, 4, '% CH4', 0.8, 1.0, 1.5),
('GAS-002', '综采面瓦斯传感器', 'GAS', 'MODBUS_RTU', '综采工作面C', 116.51, 39.81, 518.3, 1, 0, 4, '% CH4', 0.8, 1.0, 1.5),
('DUST-001', '掘进面粉尘传感器', 'DUST', 'MODBUS_RTU', '掘进工作面B', 116.49, 39.82, 525.1, 5, 0, 1000, 'mg/m³', 200, 500, 1000),
('DUST-002', '运输巷粉尘传感器', 'DUST', 'MODBUS_TCP', '主运输大巷', 116.48, 39.79, 530.0, 5, 0, 1000, 'mg/m³', 200, 500, 1000),
('CO-001', '主巷道CO传感器', 'CO', 'MODBUS_TCP', '主运输大巷', 116.48, 39.79, 530.0, 2, 0, 500, 'ppm', 24, 50, 100),
('CO-002', '机电硐室CO传感器', 'CO', 'OPC_UA', '机电硐室', 116.47, 39.78, 535.5, 2, 0, 500, 'ppm', 24, 50, 100),
('TEMP-001', '综采面温度传感器', 'TEMPERATURE', 'MODBUS_TCP', '综采工作面C', 116.51, 39.81, 518.3, 5, -5, 100, '℃', 26, 30, 35),
('TEMP-002', '机电硐室温度传感器', 'TEMPERATURE', 'OPC_UA', '机电硐室', 116.47, 39.78, 535.5, 5, -5, 100, '℃', 30, 35, 40),
('WIND-001', '通风巷风速传感器', 'WIND', 'MODBUS_RTU', '回风上山', 116.52, 39.83, 515.0, 10, 0, 15, 'm/s', 0.5, 0.3, 0.2),
('WIND-002', '进风巷风速传感器', 'WIND', 'CAN', '进风大巷', 116.46, 39.77, 540.0, 10, 0, 15, 'm/s', 0.5, 0.3, 0.2);

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
