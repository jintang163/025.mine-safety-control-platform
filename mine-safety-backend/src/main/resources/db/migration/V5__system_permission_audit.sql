-- ==================== 系统权限与运维告警 - 数据库迁移脚本 ====================

-- ==================== 用户表 ====================
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(256) NOT NULL COMMENT '密码(BCrypt加密)',
    real_name VARCHAR(64) COMMENT '真实姓名',
    email VARCHAR(128) COMMENT '邮箱',
    phone VARCHAR(32) COMMENT '手机号',
    avatar VARCHAR(512) COMMENT '头像URL',
    department VARCHAR(128) COMMENT '所属部门',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常, 2-锁定',
    last_login_time DATETIME COMMENT '最后登录时间',
    last_login_ip VARCHAR(64) COMMENT '最后登录IP',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ==================== 角色表 ====================
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL UNIQUE COMMENT '角色编码',
    role_name VARCHAR(128) NOT NULL COMMENT '角色名称',
    description VARCHAR(512) COMMENT '角色描述',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_role_code (role_code),
    INDEX idx_status (status),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- ==================== 权限表 ====================
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL UNIQUE COMMENT '权限编码',
    permission_name VARCHAR(128) NOT NULL COMMENT '权限名称',
    permission_type VARCHAR(32) DEFAULT 'BUTTON' COMMENT '权限类型: MENU-菜单, BUTTON-按钮, API-接口',
    parent_id BIGINT DEFAULT 0 COMMENT '父权限ID',
    path VARCHAR(256) COMMENT '路由路径(前端)',
    component VARCHAR(256) COMMENT '前端组件',
    icon VARCHAR(64) COMMENT '图标',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    description VARCHAR(512) COMMENT '权限描述',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_permission_code (permission_code),
    INDEX idx_parent_id (parent_id),
    INDEX idx_permission_type (permission_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限表';

-- ==================== 用户角色关联表 ====================
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id),
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ==================== 角色权限关联表 ====================
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id),
    UNIQUE KEY uk_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ==================== 操作日志表 ====================
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operation_no VARCHAR(64) NOT NULL UNIQUE COMMENT '操作编号',
    user_id BIGINT COMMENT '操作用户ID',
    username VARCHAR(64) COMMENT '操作用户名',
    real_name VARCHAR(64) COMMENT '操作人姓名',
    operation_type VARCHAR(32) NOT NULL COMMENT '操作类型: CREATE,UPDATE,DELETE,QUERY,APPROVE,ACKNOWLEDGE等',
    operation_module VARCHAR(32) NOT NULL COMMENT '操作模块: USER,ROLE,SENSOR,ALERT,THRESHOLD等',
    operation_desc VARCHAR(512) COMMENT '操作描述',
    request_method VARCHAR(16) COMMENT '请求方法: GET,POST,PUT,DELETE',
    request_url VARCHAR(512) COMMENT '请求URL',
    request_params TEXT COMMENT '请求参数(JSON)',
    response_result TEXT COMMENT '响应结果(JSON)',
    ip_address VARCHAR(64) COMMENT '操作IP地址',
    user_agent VARCHAR(512) COMMENT '用户代理',
    cost_time BIGINT COMMENT '耗时(毫秒)',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-失败, 1-成功',
    error_msg VARCHAR(1024) COMMENT '错误信息',
    target_id VARCHAR(64) COMMENT '操作目标ID',
    target_type VARCHAR(32) COMMENT '操作目标类型',
    old_value TEXT COMMENT '变更前值(JSON)',
    new_value TEXT COMMENT '变更后值(JSON)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_operation_no (operation_no),
    INDEX idx_user_id (user_id),
    INDEX idx_username (username),
    INDEX idx_operation_type (operation_type),
    INDEX idx_operation_module (operation_module),
    INDEX idx_created_at (created_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志审计表';

-- ==================== 初始化角色数据 ====================
INSERT IGNORE INTO sys_role (role_code, role_name, description, sort_order, status) VALUES
('ADMIN', '系统管理员', '拥有所有系统权限，负责系统配置和用户管理', 1, 1),
('MINE_MANAGER', '矿长', '全局查看所有数据，审批阈值调整，具有最高管理权限', 2, 1),
('SAFETY_DIRECTOR', '安全科长', '查看监控数据，确认报警，管理报警处置', 3, 1),
('SHIFT_SUPERVISOR', '值班员', '实时监控数据，处置报警，记录处置结果', 4, 1),
('MAINTENANCE_WORKER', '维修工', '设备管理，传感器维护校准，故障处理', 5, 1);

-- ==================== 初始化权限数据 ====================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, component, icon, sort_order, description, status) VALUES
-- 仪表盘
('dashboard:view', '仪表盘查看', 'MENU', 0, '/dashboard', 'Dashboard', 'Dashboard', 1, '查看监控仪表盘', 1),
-- 传感器管理
('sensor:view', '传感器查看', 'MENU', 0, '/sensor', 'SensorList', 'Sensor', 2, '查看传感器列表和详情', 1),
('sensor:edit', '传感器编辑', 'BUTTON', 2, NULL, NULL, NULL, 0, '新增/修改传感器信息', 1),
('sensor:delete', '传感器删除', 'BUTTON', 2, NULL, NULL, NULL, 0, '删除传感器', 1),
-- 报警管理
('alert:view', '报警查看', 'MENU', 0, '/alert', 'AlertList', 'Alert', 3, '查看报警记录', 1),
('alert:acknowledge', '报警确认', 'BUTTON', 5, NULL, NULL, NULL, 0, '确认报警', 1),
('alert:disposal', '报警处置', 'BUTTON', 5, NULL, NULL, NULL, 0, '处置报警', 1),
-- 阈值管理
('threshold:view', '阈值查看', 'MENU', 0, '/threshold', 'ThresholdConfig', 'Settings', 4, '查看阈值配置', 1),
('threshold:edit', '阈值修改', 'BUTTON', 8, NULL, NULL, NULL, 0, '修改阈值', 1),
('threshold:approve', '阈值审批', 'BUTTON', 8, NULL, NULL, NULL, 0, '审批阈值调整', 1),
('threshold:apply', '阈值申请', 'BUTTON', 8, NULL, NULL, NULL, 0, '申请阈值调整', 1),
-- 设备管理
('device:view', '设备查看', 'MENU', 0, '/device', 'DeviceList', 'Device', 5, '查看设备列表', 1),
('device:edit', '设备编辑', 'BUTTON', 12, NULL, NULL, NULL, 0, '新增/修改设备', 1),
('device:maintenance', '设备维护', 'BUTTON', 12, NULL, NULL, NULL, 0, '设备维护记录', 1),
('device:calibration', '设备校准', 'BUTTON', 12, NULL, NULL, NULL, 0, '设备校准记录', 1),
-- 报表管理
('report:view', '报表查看', 'MENU', 0, '/report', 'ReportList', 'Document', 6, '查看报表', 1),
('report:generate', '报表生成', 'BUTTON', 17, NULL, NULL, NULL, 0, '生成报表', 1),
('report:export', '报表导出', 'BUTTON', 17, NULL, NULL, NULL, 0, '导出报表', 1),
-- 系统管理
('system:user', '用户管理', 'MENU', 0, '/system/user', 'UserManage', 'User', 7, '用户管理菜单', 1),
('system:role', '角色管理', 'MENU', 0, '/system/role', 'RoleManage', 'Team', 8, '角色管理菜单', 1),
('system:log', '操作日志', 'MENU', 0, '/system/log', 'OperationLog', 'FileText', 9, '操作日志查看', 1),
('system:monitor', '系统监控', 'MENU', 0, '/system/monitor', 'SystemMonitor', 'Monitor', 10, '系统监控', 1),
('system:config', '系统配置', 'MENU', 0, '/system/config', 'SystemConfig', 'Setting', 11, '系统配置', 1);

-- ==================== 角色权限关联 ====================
-- 系统管理员 - 所有权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE deleted = 0;

-- 矿长 - 全局查看+审批阈值
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
(2, 1),
(2, 2),
(2, 5),
(2, 6),
(2, 8),
(2, 10),
(2, 12),
(2, 17),
(2, 18),
(2, 22),
(2, 23);

-- 安全科长 - 查看+确认报警
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
(3, 1),
(3, 2),
(3, 5),
(3, 6),
(3, 7),
(3, 8),
(3, 11),
(3, 12),
(3, 17),
(3, 22);

-- 值班员 - 处置报警
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
(4, 1),
(4, 2),
(4, 5),
(4, 7),
(4, 8),
(4, 12),
(4, 17);

-- 维修工 - 设备管理
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
(5, 1),
(5, 2),
(5, 12),
(5, 13),
(5, 14),
(5, 15);

-- ==================== 初始化用户数据 ====================
-- 密码都是: 123456 (BCrypt加密)
-- $2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2 是 123456 的BCrypt哈希
INSERT IGNORE INTO sys_user (username, password, real_name, email, phone, department, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'admin@mine-safety.com', '13800000000', '信息中心', 1),
('mine_manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张矿长', 'manager@mine-safety.com', '13800000001', '矿领导', 1),
('safety_director', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李安全科长', 'safety@mine-safety.com', '13800000002', '安全科', 1),
('shift_supervisor', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王值班员', 'duty@mine-safety.com', '13800000003', '调度室', 1),
('maintenance_worker', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '赵维修工', 'maintenance@mine-safety.com', '13800000004', '机电科', 1);

-- ==================== 用户角色关联 ====================
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 4),
(5, 5);
