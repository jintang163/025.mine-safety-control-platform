#!/bin/bash

FLINK_HOME=${FLINK_HOME:-"/opt/flink"}
JAR_NAME="mine-safety-backend-1.0.0.jar"
JOB_CLASS="com.mine.safety.flink.SensorThresholdAlertJob"
JOB_NAME="Sensor-Threshold-Alert-Job"

KAFKA_BOOTSTRAP=${KAFKA_BOOTSTRAP:-"localhost:29092"}
KAFKA_SENSOR_TOPIC=${KAFKA_SENSOR_TOPIC:-"mine.sensor.data"}
KAFKA_ALERT_TOPIC=${KAFKA_ALERT_TOPIC:-"mine.alerts"}
KAFKA_CONSUMER_GROUP=${KAFKA_CONSUMER_GROUP:-"flink-threshold-alert-group"}

REDIS_HOST=${REDIS_HOST:-"localhost"}
REDIS_PORT=${REDIS_PORT:-"6379"}
REDIS_PASSWORD=${REDIS_PASSWORD:-"mine_safety_2024"}
REDIS_THRESHOLD_KEY_PREFIX=${REDIS_THRESHOLD_KEY_PREFIX:-"threshold:"}

PARALLELISM=${PARALLELISM:-"2"}
CHECKPOINT_INTERVAL=${CHECKPOINT_INTERVAL:-"60000"}
CHECKPOINT_DIR=${CHECKPOINT_DIR:-"file:///tmp/flink/checkpoints"}

echo "========================================"
echo "  Flink 作业部署脚本"
echo "========================================"
echo "JAR 文件: $JAR_NAME"
echo "主类: $JOB_CLASS"
echo "作业名称: $JOB_NAME"
echo "并行度: $PARALLELISM"
echo ""
echo "Kafka Bootstrap: $KAFKA_BOOTSTRAP"
echo "Kafka 传感器主题: $KAFKA_SENSOR_TOPIC"
echo "Kafka 报警主题: $KAFKA_ALERT_TOPIC"
echo "Kafka 消费组: $KAFKA_CONSUMER_GROUP"
echo ""
echo "Redis Host: $REDIS_HOST"
echo "Redis Port: $REDIS_PORT"
echo "Redis 阈值键前缀: $REDIS_THRESHOLD_KEY_PREFIX"
echo ""
echo "Checkpoint 间隔: ${CHECKPOINT_INTERVAL}ms"
echo "Checkpoint 目录: $CHECKPOINT_DIR"
echo "========================================"
echo ""

$FLINK_HOME/bin/flink run \
  -c $JOB_CLASS \
  -p $PARALLELISM \
  -d \
  $JAR_NAME \
  --kafka.bootstrap.servers=$KAFKA_BOOTSTRAP \
  --kafka.sensor.topic=$KAFKA_SENSOR_TOPIC \
  --kafka.alert.topic=$KAFKA_ALERT_TOPIC \
  --kafka.consumer.group=$KAFKA_CONSUMER_GROUP \
  --redis.host=$REDIS_HOST \
  --redis.port=$REDIS_PORT \
  --redis.password=$REDIS_PASSWORD \
  --redis.threshold.key-prefix=$REDIS_THRESHOLD_KEY_PREFIX \
  --checkpoint.interval=$CHECKPOINT_INTERVAL \
  --checkpoint.dir=$CHECKPOINT_DIR \
  --job.name="$JOB_NAME"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Flink 作业提交成功！"
    echo "查看作业状态: $FLINK_HOME/bin/flink list -r"
    echo "查看作业详情: $FLINK_HOME/bin/flink info <job-id>"
    echo "取消作业: $FLINK_HOME/bin/flink cancel <job-id>"
else
    echo ""
    echo "❌ Flink 作业提交失败！"
    exit 1
fi
