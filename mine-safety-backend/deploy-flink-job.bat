@echo off
setlocal enabledelayedexpansion

set FLINK_HOME=%FLINK_HOME%
if "%FLINK_HOME%"=="" set FLINK_HOME=C:\opt\flink

set JAR_NAME=mine-safety-backend-1.0.0.jar
set JOB_CLASS=com.mine.safety.flink.SensorThresholdAlertJob
set JOB_NAME=Sensor-Threshold-Alert-Job

set KAFKA_BOOTSTRAP=%KAFKA_BOOTSTRAP%
if "%KAFKA_BOOTSTRAP%"=="" set KAFKA_BOOTSTRAP=localhost:29092

set KAFKA_SENSOR_TOPIC=%KAFKA_SENSOR_TOPIC%
if "%KAFKA_SENSOR_TOPIC%"=="" set KAFKA_SENSOR_TOPIC=mine.sensor.data

set KAFKA_ALERT_TOPIC=%KAFKA_ALERT_TOPIC%
if "%KAFKA_ALERT_TOPIC%"=="" set KAFKA_ALERT_TOPIC=mine.alerts

set KAFKA_CONSUMER_GROUP=%KAFKA_CONSUMER_GROUP%
if "%KAFKA_CONSUMER_GROUP%"=="" set KAFKA_CONSUMER_GROUP=flink-threshold-alert-group

set REDIS_HOST=%REDIS_HOST%
if "%REDIS_HOST%"=="" set REDIS_HOST=localhost

set REDIS_PORT=%REDIS_PORT%
if "%REDIS_PORT%"=="" set REDIS_PORT=6379

set REDIS_PASSWORD=%REDIS_PASSWORD%
if "%REDIS_PASSWORD%"=="" set REDIS_PASSWORD=mine_safety_2024

set REDIS_THRESHOLD_KEY_PREFIX=%REDIS_THRESHOLD_KEY_PREFIX%
if "%REDIS_THRESHOLD_KEY_PREFIX%"=="" set REDIS_THRESHOLD_KEY_PREFIX=threshold:

set PARALLELISM=%PARALLELISM%
if "%PARALLELISM%"=="" set PARALLELISM=2

set CHECKPOINT_INTERVAL=%CHECKPOINT_INTERVAL%
if "%CHECKPOINT_INTERVAL%"=="" set CHECKPOINT_INTERVAL=60000

set CHECKPOINT_DIR=%CHECKPOINT_DIR%
if "%CHECKPOINT_DIR%"=="" set CHECKPOINT_DIR=file:///C:/tmp/flink/checkpoints

echo ========================================
echo   Flink Job Deploy Script (Windows)
echo ========================================
echo JAR File: %JAR_NAME%
echo Main Class: %JOB_CLASS%
echo Job Name: %JOB_NAME%
echo Parallelism: %PARALLELISM%
echo.
echo Kafka Bootstrap: %KAFKA_BOOTSTRAP%
echo Kafka Sensor Topic: %KAFKA_SENSOR_TOPIC%
echo Kafka Alert Topic: %KAFKA_ALERT_TOPIC%
echo Kafka Consumer Group: %KAFKA_CONSUMER_GROUP%
echo.
echo Redis Host: %REDIS_HOST%
echo Redis Port: %REDIS_PORT%
echo Redis Threshold Key Prefix: %REDIS_THRESHOLD_KEY_PREFIX%
echo.
echo Checkpoint Interval: %CHECKPOINT_INTERVAL%ms
echo Checkpoint Directory: %CHECKPOINT_DIR%
echo ========================================
echo.

"%FLINK_HOME%\bin\flink.bat" run ^
  -c %JOB_CLASS% ^
  -p %PARALLELISM% ^
  -d ^
  %JAR_NAME% ^
  --kafka.bootstrap.servers=%KAFKA_BOOTSTRAP% ^
  --kafka.sensor.topic=%KAFKA_SENSOR_TOPIC% ^
  --kafka.alert.topic=%KAFKA_ALERT_TOPIC% ^
  --kafka.consumer.group=%KAFKA_CONSUMER_GROUP% ^
  --redis.host=%REDIS_HOST% ^
  --redis.port=%REDIS_PORT% ^
  --redis.password=%REDIS_PASSWORD% ^
  --redis.threshold.key-prefix=%REDIS_THRESHOLD_KEY_PREFIX% ^
  --checkpoint.interval=%CHECKPOINT_INTERVAL% ^
  --checkpoint.dir=%CHECKPOINT_DIR% ^
  --job.name="%JOB_NAME%"

if %errorlevel% equ 0 (
    echo.
    echo [OK] Flink job submitted successfully!
    echo List running jobs: %FLINK_HOME%\bin\flink.bat list -r
    echo Job info: %FLINK_HOME%\bin\flink.bat info ^<job-id^>
    echo Cancel job: %FLINK_HOME%\bin\flink.bat cancel ^<job-id^>
) else (
    echo.
    echo [ERROR] Flink job submission failed!
    exit /b 1
)

endlocal
