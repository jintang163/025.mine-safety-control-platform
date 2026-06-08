@echo off
chcp 65001 >nul
echo ========================================
echo  停止基础设施服务
echo ========================================
echo.

cd /d "%~dp0\..\docker"

echo 正在停止所有服务...
docker-compose down

echo.
echo 所有服务已停止
echo.
pause
