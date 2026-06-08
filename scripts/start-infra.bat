@echo off
chcp 65001 >nul
echo ========================================
echo  煤矿安全生产风险管控平台 - 基础设施启动
echo ========================================
echo.

cd /d "%~dp0\..\docker"

echo [1/4] 检查Docker环境...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未检测到Docker，请先安装Docker Desktop
    pause
    exit /b 1
)
echo Docker环境正常

echo.
echo [2/4] 创建必要的数据目录...
if not exist "emqx\data" mkdir emqx\data
if not exist "emqx\log" mkdir emqx\log
if not exist "emqx\etc" mkdir emqx\etc
if not exist "kafka\data" mkdir kafka\data
if not exist "influxdb\data" mkdir influxdb\data
if not exist "influxdb\config" mkdir influxdb\config
if not exist "mysql\data" mkdir mysql\data
if not exist "redis\data" mkdir redis\data
if not exist "grafana\data" mkdir grafana\data
if not exist "grafana\provisioning" mkdir grafana\provisioning
if not exist "edgex\devices" mkdir edgex\devices
echo 目录创建完成

echo.
echo [3/4] 启动核心基础设施（EMQX, Kafka, InfluxDB, MySQL, Redis）...
docker-compose up -d emqx zookeeper kafka kafka-ui influxdb mysql redis

echo.
echo [4/4] 等待服务启动...
echo 正在等待服务就绪，请稍候...
timeout /t 30 /nobreak >nul

echo.
echo ========================================
echo  基础设施启动完成！
echo ========================================
echo.
echo 服务访问地址:
echo   - EMQX Dashboard:  http://localhost:18083 (admin/public)
echo   - Kafka UI:        http://localhost:8090
echo   - InfluxDB:        http://localhost:8086 (admin/mine_safety_2024)
echo   - MySQL:           localhost:3306 (mine_safety/mine_safety_2024)
echo   - Redis:           localhost:6379 (mine_safety_2024)
echo.
echo 启动EdgeX Foundry（可选）:
echo   docker-compose up -d edgex-core-data edgex-core-metadata edgex-core-command
echo   docker-compose up -d edgex-device-modbus edgex-device-opcua edgex-app-mqtt
echo.
echo 启动Grafana（可选）:
echo   docker-compose up -d grafana
echo   http://localhost:3000 (admin/mine_safety_2024)
echo.
pause
