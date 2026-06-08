package com.mine.safety.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.mine.safety.config.InfluxDBConfig;
import com.mine.safety.dto.SensorDataDTO;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InfluxDBService {

    private final InfluxDBClient influxDBClient;
    private final WriteApi writeApi;
    private final InfluxDBConfig influxDBConfig;

    public void writeSensorData(SensorDataDTO dto) {
        try {
            Instant time = dto.getTimestamp() != null
                    ? dto.getTimestamp().atZone(ZoneId.systemDefault()).toInstant()
                    : Instant.now();

            Map<String, String> tags = new HashMap<>();
            tags.put("sensor_id", dto.getSensorId());
            if (dto.getSensorType() != null) {
                tags.put("sensor_type", dto.getSensorType());
            }
            if (dto.getLocation() != null) {
                tags.put("location", dto.getLocation());
            }
            if (dto.getProtocol() != null) {
                tags.put("protocol", dto.getProtocol());
            }

            Map<String, Object> fields = new HashMap<>();
            fields.put("value", dto.getValue().doubleValue());
            fields.put("quality", dto.getQuality());
            if (dto.getCoordinatesX() != null) {
                fields.put("coordinates_x", dto.getCoordinatesX());
                fields.put("coordinates_y", dto.getCoordinatesY());
                fields.put("coordinates_z", dto.getCoordinatesZ());
            }

            writeApi.writePoint(influxDBConfig.getBucket(), influxDBConfig.getOrg(),
                    time, WritePrecision.MS, "sensor_data", tags, fields);
        } catch (Exception e) {
            log.error("写入InfluxDB失败: {}", e.getMessage());
        }
    }

    public List<SensorDataDTO> querySensorData(String sensorId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String flux = String.format(
                    "from(bucket: \"%s\") " +
                    "|> range(start: %d, stop: %d) " +
                    "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\" and r[\"sensor_id\"] == \"%s\") " +
                    "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                    "|> sort(columns: [\"_time\"], desc: true) " +
                    "|> limit(n: 1000)",
                    influxDBConfig.getBucket(),
                    startTime.atZone(ZoneId.systemDefault()).toEpochSecond(),
                    endTime.atZone(ZoneId.systemDefault()).toEpochSecond(),
                    sensorId
            );

            return executeQuery(flux);
        } catch (Exception e) {
            log.error("查询InfluxDB失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<SensorDataDTO> querySensorDataWithAggregation(String sensorId, LocalDateTime startTime,
                                                        LocalDateTime endTime, String aggregation, String window) {
        try {
            String flux = String.format(
                    "from(bucket: \"%s\") " +
                    "|> range(start: %d, stop: %d) " +
                    "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\" and r[\"sensor_id\"] == \"%s\") " +
                    "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                    "|> aggregateWindow(every: %s, fn: %s, createEmpty: false) " +
                    "|> sort(columns: [\"_time\"], desc: true)",
                    influxDBConfig.getBucket(),
                    startTime.atZone(ZoneId.systemDefault()).toEpochSecond(),
                    endTime.atZone(ZoneId.systemDefault()).toEpochSecond(),
                    sensorId,
                    window,
                    aggregation
            );

            return executeQuery(flux);
        } catch (Exception e) {
            log.error("聚合查询InfluxDB失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<SensorDataDTO> executeQuery(String flux) {
        List<SensorDataDTO> result = new ArrayList<>();
        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(flux, influxDBConfig.getOrg());

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    SensorDataDTO dto = new SensorDataDTO();
                    dto.setSensorId(record.getValueByKey("sensor_id", String.class, null));
                    dto.setValue(java.math.BigDecimal.valueOf(((Number) record.getValue()).doubleValue()));
                    dto.setTimestamp(LocalDateTime.ofInstant(record.getTime(), ZoneId.systemDefault()));
                    dto.setSensorType(record.getValueByKey("sensor_type", String.class, null));
                    dto.setLocation(record.getValueByKey("location", String.class, null));
                    result.add(dto);
                }
            }
        } catch (Exception e) {
            log.error("执行Flux查询失败: {}", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> queryStatistics(String sensorId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();
        try {
            String flux = String.format(
                    "data = from(bucket: \"%s\") " +
                    "|> range(start: %d, stop: %d) " +
                    "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\" and r[\"sensor_id\"] == \"%s\") " +
                    "|> filter(fn: (r) => r[\"_field\"] == \"value\")\n" +
                    "data |> mean() |> yield(name: \"mean\")\n" +
                    "data |> max() |> yield(name: \"max\")\n" +
                    "data |> min() |> yield(name: \"min\")\n",
                    influxDBConfig.getBucket(),
                    startTime.atZone(ZoneId.systemDefault()).toEpochSecond(),
                    endTime.atZone(ZoneId.systemDefault()).toEpochSecond(),
                    sensorId
            );

            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(flux, influxDBConfig.getOrg());

            for (FluxTable table : tables) {
                if (!table.getRecords().isEmpty()) {
                    FluxRecord record = table.getRecords().get(0);
                    String resultName = table.getResultName();
                    Object value = record.getValue();
                    stats.put(resultName, value);
                }
            }
        } catch (Exception e) {
            log.error("查询统计数据失败: {}", e.getMessage());
        }
        return stats;
    }

    @PreDestroy
    public void close() {
        try {
            writeApi.close();
            influxDBClient.close();
        } catch (Exception e) {
            log.error("关闭InfluxDB连接失败: {}", e.getMessage());
        }
    }
}
