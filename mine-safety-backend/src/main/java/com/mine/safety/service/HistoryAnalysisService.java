package com.mine.safety.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.mine.safety.config.InfluxDBConfig;
import com.mine.safety.domain.Alert;
import com.mine.safety.domain.Sensor;
import com.mine.safety.dto.HistoryStatisticsDTO;
import com.mine.safety.repository.AlertRepository;
import com.mine.safety.repository.SensorRepository;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryAnalysisService {

    private final InfluxDBClient influxDBClient;
    private final InfluxDBConfig influxDBConfig;
    private final SensorRepository sensorRepository;
    private final AlertRepository alertRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public HistoryStatisticsDTO getHistoryStatistics(String sensorId, String startDate, String endDate, String timeDimension) {
        Sensor sensor = sensorRepository.selectOne(
                new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, sensorId));
        if (sensor == null) {
            throw new RuntimeException("传感器不存在: " + sensorId);
        }

        LocalDateTime start = LocalDate.parse(startDate, DATE_FMT).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate, DATE_FMT).plusDays(1).atStartOfDay();

        HistoryStatisticsDTO dto = new HistoryStatisticsDTO();
        dto.setSensorId(sensorId);
        dto.setSensorName(sensor.getName());
        dto.setSensorType(sensor.getType());
        dto.setLocation(sensor.getLocation());
        dto.setZoneCode(sensor.getZoneCode());
        dto.setUnit(sensor.getUnit());
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        dto.setTimeDimension(timeDimension);

        queryAggregateStats(dto, sensorId, start, end);
        queryOverThresholdStats(dto, sensor, start, end);
        queryTimeSeriesData(dto, sensorId, start, end, timeDimension);

        return dto;
    }

    public List<HistoryStatisticsDTO> getHistoryStatisticsByType(String sensorType, String zoneCode,
                                                                   String startDate, String endDate, String timeDimension) {
        LambdaQueryWrapper<Sensor> wrapper = new LambdaQueryWrapper<Sensor>()
                .eq(Sensor::getType, sensorType);
        if (zoneCode != null) {
            wrapper.eq(Sensor::getZoneCode, zoneCode);
        }

        List<Sensor> sensors = sensorRepository.selectList(wrapper);
        return sensors.stream()
                .map(s -> getHistoryStatistics(s.getSensorId(), startDate, endDate, timeDimension))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getOverviewStatistics(String sensorType, String zoneCode,
                                                      String startDate, String endDate) {
        LocalDateTime start = LocalDate.parse(startDate, DATE_FMT).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate, DATE_FMT).plusDays(1).atStartOfDay();

        Map<String, Object> overview = new HashMap<>();
        overview.put("startDate", startDate);
        overview.put("endDate", endDate);
        overview.put("sensorType", sensorType);

        String typeFilter = sensorType != null ? " and r[\"sensor_type\"] == \"" + sensorType + "\"" : "";
        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: %d, stop: %d) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\"" + typeFilter + ") " +
                "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                "|> group() " +
                "|> mean() |> yield(name: \"mean\")\n" +
                "from(bucket: \"%s\") " +
                "|> range(start: %d, stop: %d) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\"" + typeFilter + ") " +
                "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                "|> group() " +
                "|> max() |> yield(name: \"max\")\n" +
                "from(bucket: \"%s\") " +
                "|> range(start: %d, stop: %d) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\"" + typeFilter + ") " +
                "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                "|> group() " +
                "|> count() |> yield(name: \"count\")",
                influxDBConfig.getBucket(), start.atZone(ZoneId.systemDefault()).toEpochSecond(),
                end.atZone(ZoneId.systemDefault()).toEpochSecond(),
                influxDBConfig.getBucket(), start.atZone(ZoneId.systemDefault()).toEpochSecond(),
                end.atZone(ZoneId.systemDefault()).toEpochSecond(),
                influxDBConfig.getBucket(), start.atZone(ZoneId.systemDefault()).toEpochSecond(),
                end.atZone(ZoneId.systemDefault()).toEpochSecond());

        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(flux, influxDBConfig.getOrg());
            for (FluxTable table : tables) {
                if (!table.getRecords().isEmpty()) {
                    FluxRecord record = table.getRecords().get(0);
                    Object value = record.getValue();
                    if (value != null) {
                        overview.put(table.getResultName(), new BigDecimal(value.toString()).setScale(4, RoundingMode.HALF_UP));
                    }
                }
            }
        } catch (Exception e) {
            log.error("查询全局统计失败: {}", e.getMessage());
        }

        LambdaQueryWrapper<Alert> alertWrapper = new LambdaQueryWrapper<Alert>()
                .ge(Alert::getCreatedAt, start)
                .lt(Alert::getCreatedAt, end);
        if (sensorType != null) {
            alertWrapper.eq(Alert::getSensorType, sensorType);
        }
        overview.put("alertCount", alertRepository.selectCount(alertWrapper));

        return overview;
    }

    private void queryAggregateStats(HistoryStatisticsDTO dto, String sensorId,
                                      LocalDateTime start, LocalDateTime end) {
        String flux = String.format(
                "data = from(bucket: \"%s\") " +
                "|> range(start: %d, stop: %d) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\" and r[\"sensor_id\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"_field\"] == \"value\")\n" +
                "data |> mean() |> yield(name: \"mean\")\n" +
                "data |> max() |> yield(name: \"max\")\n" +
                "data |> min() |> yield(name: \"min\")\n" +
                "data |> count() |> yield(name: \"count\")",
                influxDBConfig.getBucket(),
                start.atZone(ZoneId.systemDefault()).toEpochSecond(),
                end.atZone(ZoneId.systemDefault()).toEpochSecond(),
                sensorId);

        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(flux, influxDBConfig.getOrg());
            for (FluxTable table : tables) {
                if (!table.getRecords().isEmpty()) {
                    FluxRecord record = table.getRecords().get(0);
                    Object value = record.getValue();
                    String resultName = table.getResultName();
                    if (value != null) {
                        BigDecimal bd = new BigDecimal(value.toString()).setScale(4, RoundingMode.HALF_UP);
                        switch (resultName) {
                            case "mean" -> dto.setAvgValue(bd);
                            case "max" -> dto.setMaxValue(bd);
                            case "min" -> dto.setMinValue(bd);
                            case "count" -> dto.setDataCount(Long.valueOf(value.toString()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("查询聚合统计失败 - 传感器: {}, 错误: {}", sensorId, e.getMessage());
        }
    }

    private void queryOverThresholdStats(HistoryStatisticsDTO dto, Sensor sensor,
                                          LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<Alert>()
                .eq(Alert::getSensorId, sensor.getSensorId())
                .ge(Alert::getCreatedAt, start)
                .lt(Alert::getCreatedAt, end);

        List<Alert> alerts = alertRepository.selectList(wrapper);
        long warningCount = alerts.stream().filter(a -> "WARNING".equals(a.getLevel()) || "ALERT".equals(a.getLevel()) || "EMERGENCY".equals(a.getLevel())).count();
        long alarmCount = alerts.stream().filter(a -> "ALERT".equals(a.getLevel()) || "EMERGENCY".equals(a.getLevel())).count();
        long emergencyCount = alerts.stream().filter(a -> "EMERGENCY".equals(a.getLevel())).count();

        dto.setOverWarningCount(warningCount);
        dto.setOverAlarmCount(alarmCount);
        dto.setOverPowerOffCount(emergencyCount);

        calculateOverThresholdDuration(dto, sensor, start, end);
    }

    private void calculateOverThresholdDuration(HistoryStatisticsDTO dto, Sensor sensor,
                                                  LocalDateTime start, LocalDateTime end) {
        if (sensor.getWarningThreshold() == null) {
            dto.setOverThresholdDurationMinutes(BigDecimal.ZERO);
            return;
        }

        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: %d, stop: %d) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\" and r[\"sensor_id\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                "|> filter(fn: (r) => float(v: r._value) > %s) " +
                "|> aggregateWindow(every: 1m, fn: count, createEmpty: false) " +
                "|> group() " +
                "|> count()",
                influxDBConfig.getBucket(),
                start.atZone(ZoneId.systemDefault()).toEpochSecond(),
                end.atZone(ZoneId.systemDefault()).toEpochSecond(),
                sensor.getSensorId(),
                sensor.getWarningThreshold().toPlainString());

        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(flux, influxDBConfig.getOrg());
            long overThresholdMinutes = 0;
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Object value = record.getValue();
                    if (value != null) {
                        overThresholdMinutes += Long.parseLong(value.toString());
                    }
                }
            }
            dto.setOverThresholdDurationMinutes(BigDecimal.valueOf(overThresholdMinutes));
        } catch (Exception e) {
            log.warn("查询超标时长失败: {}", e.getMessage());
            dto.setOverThresholdDurationMinutes(BigDecimal.ZERO);
        }
    }

    private void queryTimeSeriesData(HistoryStatisticsDTO dto, String sensorId,
                                      LocalDateTime start, LocalDateTime end, String timeDimension) {
        String window = switch (timeDimension != null ? timeDimension : "DAY") {
            case "HOUR" -> "1h";
            case "DAY" -> "1d";
            case "MONTH" -> "30d";
            default -> "1h";
        };

        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: %d, stop: %d) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\" and r[\"sensor_id\"] == \"%s\") " +
                "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                "|> aggregateWindow(every: %s, fn: mean, createEmpty: false) " +
                "|> sort(columns: [\"_time\"])",
                influxDBConfig.getBucket(),
                start.atZone(ZoneId.systemDefault()).toEpochSecond(),
                end.atZone(ZoneId.systemDefault()).toEpochSecond(),
                sensorId, window);

        List<HistoryStatisticsDTO.TimeSeriesPoint> points = new ArrayList<>();
        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(flux, influxDBConfig.getOrg());
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    HistoryStatisticsDTO.TimeSeriesPoint point = new HistoryStatisticsDTO.TimeSeriesPoint();
                    point.setTime(record.getTime().atZone(ZoneId.systemDefault()).toLocalDateTime().format(DATETIME_FMT));
                    Object value = record.getValue();
                    if (value != null) {
                        point.setValue(new BigDecimal(value.toString()).setScale(4, RoundingMode.HALF_UP));
                    }
                    points.add(point);
                }
            }
        } catch (Exception e) {
            log.warn("查询时序数据失败: {}", e.getMessage());
        }
        dto.setTimeSeries(points);
    }

    public List<BigDecimal> getDailyAvgValues(String sensorType, String zoneCode,
                                               String startDate, int days) {
        LocalDateTime start = LocalDate.parse(startDate, DATE_FMT).atStartOfDay();
        LocalDateTime end = start.plusDays(days);

        String zoneFilter = zoneCode != null ?
                " and r[\"location\"] == \"" + zoneCode + "\"" : "";

        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: %d, stop: %d) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\" and r[\"sensor_type\"] == \"%s\"" + zoneFilter + ") " +
                "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                "|> aggregateWindow(every: 1d, fn: mean, createEmpty: false) " +
                "|> sort(columns: [\"_time\"])",
                influxDBConfig.getBucket(),
                start.atZone(ZoneId.systemDefault()).toEpochSecond(),
                end.atZone(ZoneId.systemDefault()).toEpochSecond(),
                sensorType);

        List<BigDecimal> values = new ArrayList<>();
        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(flux, influxDBConfig.getOrg());
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Object value = record.getValue();
                    if (value != null) {
                        values.add(new BigDecimal(value.toString()).setScale(4, RoundingMode.HALF_UP));
                    }
                }
            }
        } catch (Exception e) {
            log.error("查询日均值序列失败: {}", e.getMessage());
        }
        return values;
    }

    public List<BigDecimal> getOverThresholdDailyCounts(String sensorType, String zoneCode,
                                                         BigDecimal threshold, String startDate, int days) {
        LocalDateTime start = LocalDate.parse(startDate, DATE_FMT).atStartOfDay();
        LocalDateTime end = start.plusDays(days);

        String zoneFilter = zoneCode != null ?
                " and r[\"location\"] == \"" + zoneCode + "\"" : "";

        String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: %d, stop: %d) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"sensor_data\" and r[\"sensor_type\"] == \"%s\"" + zoneFilter + ") " +
                "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                "|> filter(fn: (r) => float(v: r._value) > %s) " +
                "|> aggregateWindow(every: 1d, fn: count, createEmpty: false) " +
                "|> sort(columns: [\"_time\"])",
                influxDBConfig.getBucket(),
                start.atZone(ZoneId.systemDefault()).toEpochSecond(),
                end.atZone(ZoneId.systemDefault()).toEpochSecond(),
                sensorType, threshold.toPlainString());

        List<BigDecimal> values = new ArrayList<>();
        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(flux, influxDBConfig.getOrg());
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Object value = record.getValue();
                    if (value != null) {
                        values.add(new BigDecimal(value.toString()));
                    }
                }
            }
        } catch (Exception e) {
            log.error("查询日超标计数序列失败: {}", e.getMessage());
        }
        return values;
    }
}
