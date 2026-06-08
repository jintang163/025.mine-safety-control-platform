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

/**
 * InfluxDB时序数据库服务
 * 封装InfluxDB的写入和查询操作，为传感器时序数据提供高性能存储。
 *
 * 数据模型（Line Protocol）：
 *   - Measurement: sensor_data（测量名）
 *   - Tags（索引字段，用于快速过滤）: sensor_id, sensor_type, location, protocol
 *   - Fields（数值字段，存储实际数据）: value, quality, coordinates_x/y/z
 *   - Time: 毫秒精度时间戳
 *
 * 写入策略：
 *   - 异步批量写入（500点/500ms，由InfluxDBConfig配置）
 *   - 失败自动重试（默认3次）
 *   - 毫秒精度时间戳
 *
 * @author mine-safety
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfluxDBService {

    /** InfluxDB客户端，用于执行查询 */
    private final InfluxDBClient influxDBClient;

    /** 异步写入API，支持批量写入和自动重试 */
    private final WriteApi writeApi;

    /** InfluxDB配置，包含bucket、org等信息 */
    private final InfluxDBConfig influxDBConfig;

    /**
     * 写入传感器数据到InfluxDB
     * 将处理后的传感器数据转换为InfluxDB的Point格式并写入。
     *
     * @param dto 处理后的传感器数据
     */
    public void writeSensorData(SensorDataDTO dto) {
        try {
            // 1. 准备时间戳（毫秒精度）
            Instant time = dto.getTimestamp() != null
                    ? dto.getTimestamp().atZone(ZoneId.systemDefault()).toInstant()
                    : Instant.now();

            // 2. 准备Tags（索引字段，用于快速过滤查询）
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

            // 3. 准备Fields（数值字段，存储实际测量值）
            Map<String, Object> fields = new HashMap<>();
            fields.put("value", dto.getValue().doubleValue());
            fields.put("quality", dto.getQuality());
            if (dto.getCoordinatesX() != null) {
                fields.put("coordinates_x", dto.getCoordinatesX());
                fields.put("coordinates_y", dto.getCoordinatesY());
                fields.put("coordinates_z", dto.getCoordinatesZ());
            }

            // 4. 异步写入（批量+自动重试）
            writeApi.writePoint(influxDBConfig.getBucket(), influxDBConfig.getOrg(),
                    time, WritePrecision.MS, "sensor_data", tags, fields);
        } catch (Exception e) {
            log.error("写入InfluxDB失败: {}", e.getMessage());
        }
    }

    /**
     * 查询传感器历史数据（原始数据）
     * 查询指定传感器在指定时间范围内的原始数据点，
     * 最多返回1000条（防止返回数据过大）。
     *
     * @param sensorId  传感器ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 传感器数据列表（按时间倒序）
     */
    public List<SensorDataDTO> querySensorData(String sensorId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            // Flux查询语句：按时间范围过滤、按传感器ID过滤、取value字段、倒序、限制条数
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

    /**
     * 查询传感器历史数据（带聚合）
     * 按指定时间窗口和聚合函数对数据进行降采样，
     * 适用于大范围时间查询（如查询一天的数据，按5分钟聚合）。
     *
     * 支持的聚合函数：
     *   - mean: 平均值
     *   - max: 最大值
     *   - min: 最小值
     *   - count: 计数
     *   - sum: 求和
     *   - median: 中位数
     *   - stddev: 标准差
     *
     * @param sensorId    传感器ID
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @param aggregation 聚合函数名（mean/max/min等）
     * @param window      时间窗口（如1m/5m/1h/1d）
     * @return 聚合后的传感器数据列表
     */
    public List<SensorDataDTO> querySensorDataWithAggregation(String sensorId, LocalDateTime startTime,
                                                        LocalDateTime endTime, String aggregation, String window) {
        try {
            // Flux查询语句：过滤 + 时间窗口聚合 + 排序
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

    /**
     * 执行Flux查询并解析结果
     * 通用查询执行方法，将Flux查询结果转换为SensorDataDTO列表。
     *
     * @param flux Flux查询语句
     * @return 传感器数据列表
     */
    private List<SensorDataDTO> executeQuery(String flux) {
        List<SensorDataDTO> result = new ArrayList<>();
        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(flux, influxDBConfig.getOrg());

            // 遍历查询结果（Flux返回多个Table，每个Table代表一组结果）
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

    /**
     * 查询统计数据（均值、最大值、最小值）
     * 一次查询返回指定时间范围内的多个统计指标，
     * 用于仪表盘展示和数据分析。
     *
     * @param sensorId  传感器ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计结果Map（key: mean/max/min，value: 统计值）
     */
    public Map<String, Object> queryStatistics(String sensorId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();
        try {
            // Flux查询：一次计算多个统计值（使用多个yield返回多组结果）
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

            // 解析多组结果（每个Table对应一个yield的结果）
            for (FluxTable table : tables) {
                if (!table.getRecords().isEmpty()) {
                    FluxRecord record = table.getRecords().get(0);
                    String resultName = table.getResultName();  // mean/max/min
                    Object value = record.getValue();
                    stats.put(resultName, value);
                }
            }
        } catch (Exception e) {
            log.error("查询统计数据失败: {}", e.getMessage());
        }
        return stats;
    }

    /**
     * 关闭InfluxDB连接
     * Bean销毁前自动调用，确保资源正确释放。
     * 先关闭WriteApi（确保所有缓冲数据都已写入），再关闭客户端。
     */
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
