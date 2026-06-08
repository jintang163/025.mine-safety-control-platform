@echo off
chcp 65001 >nul
echo ========================================
echo  启动传感器数据模拟器
echo ========================================
echo.

cd /d "%~dp0\..\simulator"

echo [1/2] 检查Java环境...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未检测到Java环境，请先安装JDK 17+
    pause
    exit /b 1
)
echo Java环境正常

echo [2/2] 启动传感器数据模拟器...
echo.
echo 模拟器将按以下频率生成数据:
echo   - 瓦斯传感器: 1次/秒
echo   - 粉尘传感器: 1次/秒（可配置为5秒）
echo   - CO传感器: 1次/秒（可配置为2秒）
echo   - 温度传感器: 1次/秒（可配置为5秒）
echo   - 风速传感器: 1次/秒（可配置为10秒）
echo.
echo 异常数据概率: 2%%
echo.

mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xms256m -Xmx512m"

echo.
pause
