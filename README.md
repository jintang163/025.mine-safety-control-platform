# 煤矿安全生产风险管控平台

基于 **EdgeX Foundry + EMQX + Kafka + Spring Boot + InfluxDB** 技术栈构建的煤矿/矿产企业安全生产风险管控平台，实现井下传感器数据实时采集、边缘计算处理、云端分析展示和报警联动。

## ✨ 功能特性

### 🔌 多协议传感器接入
- ✅ Modbus RTU/TCP 协议支持
- ✅ OPC UA 协议支持
- ✅ CAN 总线协议支持
- ✅ 4G/5G 无线传输协议
- ✅ 传感器类型：瓦斯(CH4)、粉尘、一氧化碳(CO)、温度、风速

### ⚙️ 边缘计算能力
- ✅ 数据去噪处理（指数平滑算法）
- ✅ 异常值检测（Z-Score 算法）
- ✅ 数据质量评估
- ✅ 断点续传机制
- ✅ 本地缓存与容错

### 📡 数据采集与传输
- ✅ 采集频率可配置（瓦斯1次/秒，粉尘5次/秒等）
- ✅ MQTT 协议上报（EMQX Broker）
- ✅ Kafka 消息队列缓冲
- ✅ 时序数据库存储（InfluxDB 2.x）
- ✅ 关系数据库存储（MySQL 8.0）

### 🚨 报警联动系统
- ✅ 多级报警阈值配置
- ✅ 持续时间条件判断
- ✅ 报警冷却机制
- ✅ 多种通知渠道（短信、邮件、语音、Webhook）
- ✅ 报警确认与处理流程
- ✅ 报警统计与分析

### 📊 监控与API
- ✅ 实时数据监控大屏接口
- ✅ 历史数据查询与聚合
- ✅ RESTful API 接口
- ✅ Grafana 可视化集成
- ✅ 传感器管理接口

### 🧪 模拟测试工具
- ✅ 内置数据模拟器
- ✅ 独立传感器数据模拟器项目
- ✅ 异常数据模拟
- ✅ 可配置模拟参数

## 🏗️ 技术架构

```
传感器设备 → EdgeX Foundry 边缘网关 → EMQX (MQTT) → Kafka 消息队列
                                                              ↓
                        前端展示 ← Spring Boot 后端 ← InfluxDB + MySQL
```

**详细架构文档**: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## 📦 项目结构

```
mine-safety-control-platform/
├── docker/                          # Docker 基础设施
│   ├── docker-compose.yml           # 所有服务编排
│   ├── emqx/                        # EMQX MQTT Broker
│   ├── kafka/                       # Kafka 消息队列
│   ├── influxdb/                    # InfluxDB 时序数据库
│   ├── mysql/                       # MySQL 关系数据库
│   │   └── init/schema.sql          # 数据库初始化脚本
│   ├── redis/                       # Redis 缓存
│   ├── edgex/                       # EdgeX Foundry 配置
│   │   └── devices/                 # 设备配置文件
│   └── grafana/                     # Grafana 可视化
├── mine-safety-backend/             # Spring Boot 后端服务
│   ├── pom.xml
│   └── src/main/java/com/mine/safety/
│       ├── MineSafetyApplication.java
│       ├── config/                  # 配置类
│       ├── domain/                  # 领域模型
│       ├── dto/                     # 数据传输对象
│       ├── repository/              # 数据访问层
│       ├── service/                 # 业务逻辑层
│       └── controller/              # REST API 控制器
├── simulator/                       # 独立传感器数据模拟器
│   ├── pom.xml
│   └── src/main/java/com/mine/simulator/
├── scripts/                         # 启动脚本
│   ├── start-infra.bat              # 启动基础设施
│   ├── stop-infra.bat               # 停止基础设施
│   ├── start-backend.bat            # 启动后端服务
│   └── start-simulator.bat          # 启动模拟器
└── docs/                            # 文档
    └── ARCHITECTURE.md              # 系统架构文档
```

## 🚀 快速开始

### 环境要求
- Docker Desktop 4.0+
- JDK 17+
- Maven 3.8+
- 8GB+ 可用内存

### 步骤一：启动基础设施

```bash
# Windows
scripts\start-infra.bat

# 或手动执行
cd docker
docker-compose up -d emqx zookeeper kafka kafka-ui influxdb mysql redis
```

**服务验证**:
- EMQX Dashboard: http://localhost:18083 (admin/public)
- Kafka UI: http://localhost:8090
- InfluxDB: http://localhost:8086 (admin/mine_safety_2024)
- MySQL: localhost:3306 (mine_safety/mine_safety_2024)

### 步骤二：编译并启动后端服务

```bash
cd mine-safety-backend
mvn clean package -DskipTests
mvn spring-boot:run
```

后端服务启动后访问: http://localhost:8080/api

### 步骤三：启动数据模拟器（可选）

**方式一：使用独立模拟器**
```bash
scripts\start-simulator.bat
```

**方式二：启用后端内置模拟器**
修改 `mine-safety-backend/src/main/resources/application.yml`:
```yaml
app:
  simulator:
    enabled: true  # 设置为true启用内置模拟器
```

### 步骤四：验证数据

调用API接口查看实时数据:
```bash
# 获取所有传感器最新数据
curl http://localhost:8080/api/sensor-data/latest

# 获取监控概览
curl http://localhost:8080/api/monitor/overview

# 获取报警统计
curl http://localhost:8080/api/alerts/statistics
```

## 📡 API 接口列表

### 传感器管理
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/sensors | 获取所有传感器列表 |
| GET | /api/sensors/{sensorId} | 获取单个传感器详情 |
| POST | /api/sensors | 新增传感器 |
| PUT | /api/sensors/{sensorId} | 更新传感器 |
| DELETE | /api/sensors/{sensorId} | 删除传感器 |
| GET | /api/sensors/types | 获取传感器类型列表 |
| GET | /api/sensors/protocols | 获取协议列表 |

### 数据查询
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/sensor-data/latest | 获取所有传感器最新数据 |
| GET | /api/sensor-data/{sensorId}/latest | 获取单个传感器最新数据 |
| GET | /api/sensor-data/{sensorId}/history | 获取历史数据 |
| GET | /api/sensor-data/{sensorId}/aggregated | 获取聚合数据 |
| GET | /api/sensor-data/{sensorId}/statistics | 获取统计数据 |

### 报警管理
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/alerts | 获取报警列表（支持分页筛选） |
| GET | /api/alerts/{alertNo} | 获取报警详情 |
| POST | /api/alerts/{alertNo}/acknowledge | 确认/处理报警 |
| GET | /api/alerts/statistics | 获取报警统计 |
| GET | /api/alerts/recent | 获取最近报警 |
| GET | /api/alerts/rules | 获取报警规则列表 |
| POST | /api/alerts/rules | 新增报警规则 |
| PUT | /api/alerts/rules/{id} | 更新报警规则 |
| DELETE | /api/alerts/rules/{id} | 删除报警规则 |

### 监控看板
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/monitor/overview | 获取系统概览 |
| GET | /api/monitor/dashboard | 获取看板数据 |
| GET | /api/monitor/real-time | 获取实时数据 |
| GET | /api/monitor/by-type/{type} | 按类型获取数据 |
| GET | /api/monitor/by-location/{location} | 按位置获取数据 |
| GET | /api/monitor/system/status | 获取系统状态 |
| GET | /api/monitor/simulator/status | 获取模拟器状态 |
| POST | /api/monitor/simulator/start | 启动模拟器 |
| POST | /api/monitor/simulator/stop | 停止模拟器 |

## ⚠️ 报警阈值配置

### 默认报警规则

| 监测指标 | 预警阈值 | 报警阈值 | 紧急阈值 | 单位 |
|---------|---------|---------|---------|------|
| 瓦斯浓度 | ≥0.8 | ≥1.0 | ≥2.0 | % CH4 |
| 粉尘浓度 | ≥200 | ≥500 | - | mg/m³ |
| 一氧化碳 | ≥24 | ≥50 | - | ppm |
| 温度 | ≥26 | ≥30 | - | ℃ |
| 风速 | ≤0.5 | ≤0.3 | - | m/s |

### 配置方式
1. 通过数据库表 `alert_rules` 配置
2. 通过 REST API `/api/alerts/rules` 动态配置
3. 支持按传感器类型或单个传感器配置规则

## 📊 Grafana 可视化

### 启动 Grafana
```bash
cd docker
docker-compose up -d grafana
```

访问: http://localhost:3000 (admin/mine_safety_2024)

已预配置数据源:
- InfluxDB: 时序数据查询
- MySQL: 业务数据查询

## 🔧 EdgeX Foundry 边缘网关

### 启动 EdgeX 服务
```bash
cd docker
docker-compose up -d edgex-core-data edgex-core-metadata edgex-core-command
docker-compose up -d edgex-device-modbus edgex-device-opcua edgex-app-mqtt
```

### 设备配置
设备配置文件位于 `docker/edgex/devices/`:
- `modbus.devices.yaml`: Modbus 设备配置
- `modbus.profiles.yaml`: 设备配置文件（数据模型）

## 📝 数据格式说明

### MQTT 消息格式
**Topic**: `mine/sensor/data/{sensorId}`

```json
{
  "sensorId": "GAS-001",
  "value": 0.85,
  "timestamp": "2024-01-15T10:30:00",
  "location": "回风巷工作面A",
  "coordinatesX": 116.5,
  "coordinatesY": 39.8,
  "coordinatesZ": 520.5,
  "unit": "% CH4",
  "sensorType": "GAS",
  "quality": 1,
  "protocol": "MODBUS_RTU"
}
```

### Kafka Topic
- `sensor-raw-data`: 原始采集数据
- `sensor-processed-data`: 处理后的数据
- `alarm-events`: 报警事件

## 🔒 安全建议

1. **修改默认密码**: 所有服务的默认密码应在生产环境中修改
2. **启用 TLS**: MQTT、API 等接口建议启用 TLS 加密
3. **网络隔离**: 井下网络与办公网络物理隔离
4. **访问控制**: 配置 API 访问权限控制
5. **定期备份**: MySQL 和 InfluxDB 数据定期备份

## 📈 性能优化建议

### JVM 参数
```bash
-Xms512m -Xmx2048m -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

### Kafka 优化
- 增加分区数提高并发
- 调整 batch.size 和 linger.ms
- 启用压缩

### InfluxDB 优化
- 合理设置数据保留策略
- 使用适当的分片策略
- 配置连续查询预聚合

## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 📄 许可证

本项目仅供学习和研究使用。

## 📞 技术支持

如有问题，请查看 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 或提交 Issue。
