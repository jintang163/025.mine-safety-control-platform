package com.mine.safety.flink;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.flink.model.SensorData;
import com.mine.safety.flink.model.ThresholdConfig;
import com.mine.safety.flink.source.KafkaSourceFactory;
import com.mine.safety.flink.source.RedisThresholdSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SensorThresholdAlertJob {

    private static final String KAFKA_SOURCE_TOPIC = "sensor-data";
    private static final String KAFKA_ALERT_TOPIC = "sensor-alerts";
    private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka:9092";
    private static final String REDIS_HOST = "redis";
    private static final int REDIS_PORT = 6379;
    private static final String THRESHOLD_CACHE_PREFIX = "threshold:sensor:";

    private static final MapStateDescriptor<String, ThresholdConfig> THRESHOLD_STATE_DESC =
            new MapStateDescriptor<>(
                    "thresholdBroadcastState",
                    TypeInformation.of(String.class),
                    TypeInformation.of(ThresholdConfig.class)
            );

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);
        env.getCheckpointConfig().setCheckpointTimeout(300000);

        KafkaSource<String> kafkaSource = KafkaSourceFactory.create(
                KAFKA_BOOTSTRAP_SERVERS,
                KAFKA_SOURCE_TOPIC,
                "flink-threshold-alert-group"
        );

        DataStream<String> rawDataStream = env
                .fromSource(kafkaSource, WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(5)),
                        "Kafka-SensorData-Source")
                .name("Kafka-SensorData-Source");

        DataStream<SensorData> sensorDataStream = rawDataStream
                .map(new SensorDataParseFunction())
                .name("Parse-SensorData")
                .filter(data -> data != null && data.getQuality() == 1)
                .name("Filter-ValidData");

        KeyedStream<SensorData, String> keyedSensorStream = sensorDataStream
                .keyBy(SensorData::getSensorId);

        DataStream<ThresholdConfig> thresholdStream = env
                .addSource(new RedisThresholdSource(REDIS_HOST, REDIS_PORT, THRESHOLD_CACHE_PREFIX))
                .name("Redis-Threshold-Source");

        BroadcastStream<ThresholdConfig> thresholdBroadcastStream = thresholdStream
                .broadcast(THRESHOLD_STATE_DESC);

        DataStream<String> alertStream = keyedSensorStream
                .connect(thresholdBroadcastStream)
                .process(new ThresholdAlertProcessFunction())
                .name("Threshold-Alert-Detection");

        KafkaSink<String> alertKafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(KAFKA_ALERT_TOPIC)
                        .setKafkaValueSerializer((element, context) ->
                                new ProducerRecord<>(KAFKA_ALERT_TOPIC,
                                        element.getBytes(StandardCharsets.UTF_8)))
                        .build())
                .build();

        alertStream.sinkTo(alertKafkaSink)
                .name("Kafka-Alert-Sink");

        alertStream.print()
                .name("Print-Alerts");

        env.execute("Sensor-Threshold-Alert-Job");
    }

    public static class SensorDataParseFunction extends RichMapFunction<String, SensorData> {
        @Override
        public SensorData map(String value) throws Exception {
            try {
                return JSON.parseObject(value, SensorData.class);
            } catch (Exception e) {
                log.warn("解析传感器数据失败: {}", value, e);
                return null;
            }
        }
    }

    public static class ThresholdAlertProcessFunction
            extends KeyedBroadcastProcessFunction<String, SensorData, ThresholdConfig, String> {

        private transient ValueState<Long> lastAlertTimeState;
        private static final long COOLDOWN_MILLIS = TimeUnit.SECONDS.toMillis(30);

        @Override
        public void open(Configuration parameters) {
            ValueStateDescriptor<Long> lastAlertDesc = new ValueStateDescriptor<>(
                    "lastAlertTime",
                    Long.class
            );
            lastAlertTimeState = getRuntimeContext().getState(lastAlertDesc);
        }

        @Override
        public void processElement(
                SensorData sensorData,
                ReadOnlyContext ctx,
                Collector<String> out) throws Exception {

            String sensorId = sensorData.getSensorId();
            ThresholdConfig threshold = ctx.getBroadcastState(THRESHOLD_STATE_DESC).get(sensorId);

            if (threshold == null) {
                return;
            }

            String alertLevel = determineAlertLevel(sensorData.getValue(), threshold);
            if (alertLevel == null) {
                return;
            }

            Long lastAlertTime = lastAlertTimeState.value();
            long currentTime = System.currentTimeMillis();
            if (lastAlertTime != null && (currentTime - lastAlertTime) < COOLDOWN_MILLIS) {
                return;
            }

            lastAlertTimeState.update(currentTime);

            String alert = createAlertJSON(sensorData, threshold, alertLevel);
            out.collect(alert);

            log.info("Flink检测到报警 - 传感器: {}, 级别: {}, 值: {}",
                    sensorId, alertLevel, sensorData.getValue());
        }

        @Override
        public void processBroadcastElement(
                ThresholdConfig threshold,
                Context ctx,
                Collector<String> out) throws Exception {

            ctx.getBroadcastState(THRESHOLD_STATE_DESC)
                    .put(threshold.getSensorId(), threshold);

            log.debug("阈值配置已更新 - 传感器: {}", threshold.getSensorId());
        }

        private String determineAlertLevel(BigDecimal value, ThresholdConfig threshold) {
            if (threshold.getPowerOffThreshold() != null &&
                    value.compareTo(threshold.getPowerOffThreshold()) >= 0) {
                return "EMERGENCY";
            }
            if (threshold.getAlarmThreshold() != null &&
                    value.compareTo(threshold.getAlarmThreshold()) >= 0) {
                return "ALERT";
            }
            if (threshold.getWarningThreshold() != null &&
                    value.compareTo(threshold.getWarningThreshold()) >= 0) {
                return "WARNING";
            }
            return null;
        }

        private String createAlertJSON(SensorData data, ThresholdConfig threshold, String level) {
            java.util.Map<String, Object> alert = new java.util.HashMap<>();
            alert.put("alertNo", "ALT-FLINK-" + System.currentTimeMillis());
            alert.put("sensorId", data.getSensorId());
            alert.put("sensorName", threshold.getSensorName());
            alert.put("sensorType", threshold.getSensorType());
            alert.put("location", data.getLocation());
            alert.put("alertValue", data.getValue());
            alert.put("thresholdValue", getTriggeredThreshold(threshold, level));
            alert.put("level", level);
            alert.put("ruleName", "Flink-三级阈值检测");
            alert.put("description", buildDescription(threshold, level, data.getValue()));
            alert.put("status", 0);
            alert.put("firstAlertTime", LocalDateTime.now().toString());
            alert.put("source", "FLINK");
            return JSON.toJSONString(alert);
        }

        private BigDecimal getTriggeredThreshold(ThresholdConfig threshold, String level) {
            return switch (level) {
                case "EMERGENCY" -> threshold.getPowerOffThreshold();
                case "ALERT" -> threshold.getAlarmThreshold();
                case "WARNING" -> threshold.getWarningThreshold();
                default -> null;
            };
        }

        private String buildDescription(ThresholdConfig threshold, String level, BigDecimal value) {
            String typeName = switch (threshold.getSensorType()) {
                case "GAS" -> "瓦斯";
                case "DUST" -> "粉尘";
                case "CO" -> "一氧化碳";
                case "TEMPERATURE" -> "温度";
                case "WIND" -> "风速";
                default -> threshold.getSensorType();
            };
            String unit = getUnitByType(threshold.getSensorType());
            String levelText = switch (level) {
                case "EMERGENCY" -> "断电阈值";
                case "ALERT" -> "报警阈值";
                case "WARNING" -> "预警阈值";
                default -> "阈值";
            };
            return String.format("%s浓度%s超过%s%s，当前值%s%s",
                    typeName, levelText, getTriggeredThreshold(threshold, level), unit, value, unit);
        }

        private String getUnitByType(String sensorType) {
            return switch (sensorType) {
                case "GAS" -> "% CH4";
                case "DUST" -> "mg/m³";
                case "CO" -> "ppm";
                case "TEMPERATURE" -> "℃";
                case "WIND" -> "m/s";
                default -> "";
            };
        }
    }
}
