package com.mine.safety.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mine.safety.domain.*;
import com.mine.safety.repository.AlertRepository;
import com.mine.safety.repository.SensorDataRepository;
import com.mine.safety.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SensorRepository sensorRepository;
    private final AlertRepository alertRepository;
    private final SensorDataRepository sensorDataRepository;
    private final AlertLifecycleService alertLifecycleService;
    private final StringRedisTemplate redisTemplate;

    public Map<String, Object> getOverview() {
        Map<String, Object> result = new HashMap<>();

        long sensorTotal = sensorRepository.selectCount(null);
        long sensorOnline = sensorRepository.selectCount(new LambdaQueryWrapper<Sensor>().eq(Sensor::getStatus, 1));
        long sensorOffline = sensorRepository.selectCount(new LambdaQueryWrapper<Sensor>().eq(Sensor::getStatus, 0));
        long sensorFault = sensorRepository.selectCount(new LambdaQueryWrapper<Sensor>().eq(Sensor::getStatus, 2));

        double onlineRate = sensorTotal > 0 ? BigDecimal.valueOf(sensorOnline)
                .divide(BigDecimal.valueOf(sensorTotal), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue() : 0.0;

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        long todayAlertTotal = alertRepository.selectCount(
                new LambdaQueryWrapper<Alert>().ge(Alert::getFirstAlertTime, todayStart));
        long todayAlertConfirmed = alertRepository.selectCount(
                new LambdaQueryWrapper<Alert>()
                        .in(Alert::getStatus, 4, 1, 5, 6)
                        .ge(Alert::getFirstAlertTime, todayStart));

        double confirmRate = todayAlertTotal > 0 ? BigDecimal.valueOf(todayAlertConfirmed)
                .divide(BigDecimal.valueOf(todayAlertTotal), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue() : 0.0;

        result.put("sensorTotal", sensorTotal);
        result.put("sensorOnline", sensorOnline);
        result.put("sensorOffline", sensorOffline);
        result.put("sensorFault", sensorFault);
        result.put("onlineRate", onlineRate);
        result.put("todayAlertTotal", todayAlertTotal);
        result.put("todayAlertConfirmed", todayAlertConfirmed);
        result.put("confirmRate", confirmRate);
        result.put("undergroundPersonnel", 0);
        result.put("timestamp", LocalDateTime.now());

        return result;
    }

    public List<Map<String, Object>> getSensorRealtime() {
        List<Sensor> sensors = sensorRepository.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Sensor sensor : sensors) {
            Map<String, Object> item = new HashMap<>();
            item.put("sensorId", sensor.getSensorId());
            item.put("sensorName", sensor.getName());
            item.put("sensorType", sensor.getType());
            item.put("location", sensor.getLocation());
            item.put("tunnel", sensor.getLocation());
            item.put("coordinatesX", sensor.getCoordinatesX());
            item.put("coordinatesY", sensor.getCoordinatesY());

            double currentValue = 0;
            try {
                String redisKey = "sensor:realtime:" + sensor.getSensorId();
                Object val = redisTemplate.opsForHash().get(redisKey, "value");
                if (val != null) {
                    currentValue = Double.parseDouble(val.toString());
                }
            } catch (Exception ignored) {
            }
            item.put("currentValue", currentValue);

            String status = "normal";
            if (sensor.getAlarmThreshold() != null && currentValue >= sensor.getAlarmThreshold().doubleValue()) {
                status = "alarm";
            } else if (sensor.getWarningThreshold() != null && currentValue >= sensor.getWarningThreshold().doubleValue()) {
                status = "warning";
            }
            item.put("unit", sensor.getUnit());
            item.put("status", status);
            item.put("lastUpdateTime", sensor.getLastOnlineTime());

            result.add(item);
        }

        return result;
    }

    public Map<String, Object> getAlertTrend() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(24);

        List<String> hours = new ArrayList<>();
        List<Long> counts = new ArrayList<>();

        Map<String, Long> typeCountMap = new LinkedHashMap<>();
        List<String> allTypes = Arrays.asList("GAS", "CO", "DUST", "TEMPERATURE", "WIND");
        for (String type : allTypes) {
            typeCountMap.put(type, 0L);
        }

        DateTimeFormatter hourFormatter = DateTimeFormatter.ofPattern("HH:mm");

        for (int i = 0; i < 24; i++) {
            LocalDateTime hourStart = start.plusHours(i);
            LocalDateTime hourEnd = hourStart.plusHours(1);
            String hourLabel = hourStart.format(hourFormatter);
            hours.add(hourLabel);

            long count = alertRepository.selectCount(
                    new LambdaQueryWrapper<Alert>()
                            .between(Alert::getFirstAlertTime, hourStart, hourEnd));
            counts.add(count);
        }

        List<Map<String, Object>> byType = new ArrayList<>();
        for (String type : allTypes) {
            List<Long> typeCounts = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                LocalDateTime hourStart = start.plusHours(i);
                LocalDateTime hourEnd = hourStart.plusHours(1);
                long typeCount = alertRepository.selectCount(
                        new LambdaQueryWrapper<Alert>()
                                .eq(Alert::getSensorType, type)
                                .between(Alert::getFirstAlertTime, hourStart, hourEnd));
                typeCounts.add(typeCount);
            }
            Map<String, Object> typeEntry = new HashMap<>();
            typeEntry.put("type", type);
            typeEntry.put("counts", typeCounts);
            byType.add(typeEntry);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("hours", hours);
        result.put("counts", counts);
        result.put("byType", byType);
        return result;
    }

    public List<Map<String, Object>> getAlertTypeDistribution() {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        List<Alert> alerts = alertRepository.selectList(
                new LambdaQueryWrapper<Alert>().ge(Alert::getFirstAlertTime, todayStart));

        Map<String, Long> grouped = alerts.stream()
                .collect(Collectors.groupingBy(Alert::getSensorType, Collectors.counting()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : grouped.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("type", entry.getKey());
            item.put("count", entry.getValue());
            result.add(item);
        }

        return result;
    }

    public Map<String, Object> getDeviceStatus() {
        long sensorTotal = sensorRepository.selectCount(null);
        long sensorOnline = sensorRepository.selectCount(new LambdaQueryWrapper<Sensor>().eq(Sensor::getStatus, 1));
        long sensorOffline = sensorRepository.selectCount(new LambdaQueryWrapper<Sensor>().eq(Sensor::getStatus, 0));
        long sensorFault = sensorRepository.selectCount(new LambdaQueryWrapper<Sensor>().eq(Sensor::getStatus, 2));

        Map<String, Object> result = new HashMap<>();
        result.put("total", sensorTotal);
        result.put("online", sensorOnline);
        result.put("offline", sensorOffline);
        result.put("fault", sensorFault);
        return result;
    }

    public List<Map<String, Object>> getPersonnelDistribution() {
        Random random = new Random();
        List<Map<String, Object>> result = new ArrayList<>();

        String[][] zones = {
                {"ZONE_01", "采煤工作面", "120.5", "45.3"},
                {"ZONE_02", "掘进工作面", "85.2", "62.8"},
                {"ZONE_03", "回风巷", "200.1", "78.6"},
                {"ZONE_04", "运输巷", "156.7", "33.9"},
                {"ZONE_05", "中央变电所", "45.3", "90.2"}
        };

        for (String[] zone : zones) {
            Map<String, Object> item = new HashMap<>();
            item.put("zoneCode", zone[0]);
            item.put("zoneName", zone[1]);
            item.put("count", random.nextInt(26) + 5);
            item.put("coordinatesX", new BigDecimal(zone[2]));
            item.put("coordinatesY", new BigDecimal(zone[3]));
            result.add(item);
        }

        return result;
    }

    public List<Map<String, Object>> getHeatmap() {
        List<Sensor> gasSensors = sensorRepository.selectList(
                new LambdaQueryWrapper<Sensor>().eq(Sensor::getType, "GAS"));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Sensor sensor : gasSensors) {
            if (sensor.getCoordinatesX() != null && sensor.getCoordinatesY() != null) {
                double currentValue = 0;
                try {
                    String redisKey = "sensor:realtime:" + sensor.getSensorId();
                    Object val = redisTemplate.opsForHash().get(redisKey, "value");
                    if (val != null) {
                        currentValue = Double.parseDouble(val.toString());
                    }
                } catch (Exception ignored) {
                }

                Map<String, Object> item = new HashMap<>();
                item.put("coordinatesX", sensor.getCoordinatesX());
                item.put("coordinatesY", sensor.getCoordinatesY());
                item.put("value", currentValue);
                result.add(item);
            }
        }

        return result;
    }

    public List<Map<String, Object>> getTunnelSensorHistory(String tunnel) {
        List<Sensor> sensors = sensorRepository.selectList(
                new LambdaQueryWrapper<Sensor>().like(Sensor::getLocation, tunnel));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Sensor sensor : sensors) {
            List<SensorData> dataList = sensorDataRepository.selectList(
                    new LambdaQueryWrapper<SensorData>()
                            .eq(SensorData::getSensorId, sensor.getSensorId())
                            .orderByDesc(SensorData::getTimestamp)
                            .last("LIMIT 60"));

            List<String> timestamps = new ArrayList<>();
            List<BigDecimal> values = new ArrayList<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
            for (int i = dataList.size() - 1; i >= 0; i--) {
                SensorData sd = dataList.get(i);
                timestamps.add(sd.getTimestamp().format(fmt));
                values.add(sd.getValue());
            }

            Map<String, Object> item = new HashMap<>();
            item.put("sensorId", sensor.getSensorId());
            item.put("sensorName", sensor.getName());
            item.put("timestamps", timestamps);
            item.put("values", values);
            result.add(item);
        }

        return result;
    }

    public List<Map<String, Object>> getTunnelAlertRecords(String tunnel) {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        List<Alert> alerts = alertRepository.selectList(
                new LambdaQueryWrapper<Alert>()
                        .eq(Alert::getTunnel, tunnel)
                        .ge(Alert::getFirstAlertTime, todayStart)
                        .orderByDesc(Alert::getCreatedAt));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Alert alert : alerts) {
            Map<String, Object> item = new HashMap<>();
            item.put("alertNo", alert.getAlertNo());
            item.put("sensorName", alert.getSensorName());
            item.put("level", alert.getLevel());
            item.put("alertValue", alert.getAlertValue());
            item.put("status", alert.getStatus());
            item.put("createdAt", alert.getCreatedAt());
            result.add(item);
        }

        return result;
    }
}
