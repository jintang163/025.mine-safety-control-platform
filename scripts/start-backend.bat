@echo off
chcp 65001 >nul
echo ========================================
echo  启动后端服务
echo ========================================
echo.

cd /d "%~dp0\..\mine-safety-backend"

echo [1/2] 检查Java环境...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未检测到Java环境，请先安装JDK 17+
    pause
    exit /b 1
)
echo Java环境正常

echo [2/2] 启动后端服务...
echo.
echo 如果需要启用内置数据模拟器，请修改 application.yml 中 app.simulator.enabled = true
echo.

mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xms512m -Xmx2048m"

echo.
pause
