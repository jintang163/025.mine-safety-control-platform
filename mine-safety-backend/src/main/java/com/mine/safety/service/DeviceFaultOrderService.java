package com.mine.safety.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mine.safety.domain.DeviceFaultOrder;
import com.mine.safety.domain.Sensor;
import com.mine.safety.dto.DeviceFaultOrderDTO;
import com.mine.safety.repository.DeviceFaultOrderRepository;
import com.mine.safety.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceFaultOrderService {

    private final DeviceFaultOrderRepository faultOrderRepository;
    private final SensorRepository sensorRepository;
    private final RestTemplate restTemplate;

    @Value("${app.fault-order.work-order-api-url:}")
    private String workOrderApiUrl;

    @Value("${app.fault-order.work-order-api-key:}")
    private String workOrderApiKey;

    @Value("${app.fault-order.notify-channels:SMS,APP}")
    private String defaultNotifyChannels;

    @Value("${app.fault-order.enabled:true}")
    private boolean faultOrderEnabled;

    private static final AtomicInteger ORDER_SEQ = new AtomicInteger(0);
    private static final DateTimeFormatter ORDER_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Transactional
    public DeviceFaultOrderDTO createFaultOrder(String sensorId, String faultType, String faultLevel,
                                                 String faultDescription, String assignee, String assigneePhone) {
        Sensor sensor = sensorRepository.selectOne(
                new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, sensorId));
        if (sensor == null) {
            throw new RuntimeException("传感器不存在: " + sensorId);
        }

        DeviceFaultOrder order = new DeviceFaultOrder();
        order.setOrderNo(generateOrderNo());
        order.setSensorId(sensorId);
        order.setSensorName(sensor.getName());
        order.setFaultType(faultType);
        order.setFaultLevel(faultLevel);
        order.setFaultDescription(faultDescription);
        order.setFaultTime(LocalDateTime.now());
        order.setLocation(sensor.getLocation());
        order.setZoneCode(sensor.getZoneCode());
        order.setAssignee(assignee);
        order.setAssigneePhone(assigneePhone);
        order.setStatus(DeviceFaultOrder.OrderStatus.PENDING.getValue());
        order.setNotifyChannels(defaultNotifyChannels);
        order.setNotifyStatus(0);

        faultOrderRepository.insert(order);
        log.warn("设备故障工单已创建 - 工单号: {}, 传感器: {}, 故障类型: {}, 级别: {}",
                order.getOrderNo(), sensorId, faultType, faultLevel);

        notifyAssignee(order);

        pushToExternalWorkOrderSystem(order);

        return convertToDTO(order);
    }

    @Transactional
    public DeviceFaultOrderDTO autoCreateOfflineFaultOrder(Sensor sensor) {
        LambdaQueryWrapper<DeviceFaultOrder> wrapper = new LambdaQueryWrapper<DeviceFaultOrder>()
                .eq(DeviceFaultOrder::getSensorId, sensor.getSensorId())
                .eq(DeviceFaultOrder::getFaultType, DeviceFaultOrder.FaultType.OFFLINE.name())
                .in(DeviceFaultOrder::getStatus,
                        DeviceFaultOrder.OrderStatus.PENDING.getValue(),
                        DeviceFaultOrder.OrderStatus.PROCESSING.getValue());

        Long existingCount = faultOrderRepository.selectCount(wrapper);
        if (existingCount > 0) {
            log.debug("传感器已有未关闭的离线工单 - 传感器: {}", sensor.getSensorId());
            return null;
        }

        String description = String.format("传感器[%s]离线超过%d分钟，最后在线时间: %s",
                sensor.getName(),
                sensor.getOfflineTimeoutMinutes() != null ? sensor.getOfflineTimeoutMinutes() : 10,
                sensor.getLastOnlineTime() != null ? sensor.getLastOnlineTime().toString() : "未知");

        return createFaultOrder(sensor.getSensorId(),
                DeviceFaultOrder.FaultType.OFFLINE.name(),
                DeviceFaultOrder.FaultLevel.HIGH.name(),
                description,
                null,
                null);
    }

    @Transactional
    public DeviceFaultOrderDTO autoCreateLowBatteryFaultOrder(Sensor sensor) {
        LambdaQueryWrapper<DeviceFaultOrder> wrapper = new LambdaQueryWrapper<DeviceFaultOrder>()
                .eq(DeviceFaultOrder::getSensorId, sensor.getSensorId())
                .eq(DeviceFaultOrder::getFaultType, DeviceFaultOrder.FaultType.LOW_BATTERY.name())
                .in(DeviceFaultOrder::getStatus,
                        DeviceFaultOrder.OrderStatus.PENDING.getValue(),
                        DeviceFaultOrder.OrderStatus.PROCESSING.getValue());

        Long existingCount = faultOrderRepository.selectCount(wrapper);
        if (existingCount > 0) {
            return null;
        }

        String description = String.format("传感器[%s]电量不足(%d%%)，请及时更换电池或充电",
                sensor.getName(), sensor.getBatteryLevel());

        return createFaultOrder(sensor.getSensorId(),
                DeviceFaultOrder.FaultType.LOW_BATTERY.name(),
                DeviceFaultOrder.FaultLevel.MEDIUM.name(),
                description,
                null,
                null);
    }

    public List<DeviceFaultOrderDTO> getFaultOrders(String sensorId, Integer status, String faultType) {
        LambdaQueryWrapper<DeviceFaultOrder> wrapper = new LambdaQueryWrapper<>();
        if (sensorId != null) {
            wrapper.eq(DeviceFaultOrder::getSensorId, sensorId);
        }
        if (status != null) {
            wrapper.eq(DeviceFaultOrder::getStatus, status);
        }
        if (faultType != null) {
            wrapper.eq(DeviceFaultOrder::getFaultType, faultType);
        }
        wrapper.orderByDesc(DeviceFaultOrder::getCreatedAt);

        return faultOrderRepository.selectList(wrapper).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public DeviceFaultOrderDTO getFaultOrder(String orderNo) {
        DeviceFaultOrder order = faultOrderRepository.selectOne(
                new LambdaQueryWrapper<DeviceFaultOrder>().eq(DeviceFaultOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new RuntimeException("工单不存在: " + orderNo);
        }
        return convertToDTO(order);
    }

    @Transactional
    public DeviceFaultOrderDTO assignFaultOrder(String orderNo, String assignee, String assigneePhone) {
        DeviceFaultOrder order = faultOrderRepository.selectOne(
                new LambdaQueryWrapper<DeviceFaultOrder>().eq(DeviceFaultOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new RuntimeException("工单不存在: " + orderNo);
        }

        order.setAssignee(assignee);
        order.setAssigneePhone(assigneePhone);
        order.setStatus(DeviceFaultOrder.OrderStatus.PROCESSING.getValue());
        faultOrderRepository.updateById(order);

        notifyAssignee(order);
        log.info("故障工单已指派 - 工单号: {}, 指派给: {}", orderNo, assignee);
        return convertToDTO(order);
    }

    @Transactional
    public DeviceFaultOrderDTO resolveFaultOrder(String orderNo, String resolution, String resolvedBy) {
        DeviceFaultOrder order = faultOrderRepository.selectOne(
                new LambdaQueryWrapper<DeviceFaultOrder>().eq(DeviceFaultOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new RuntimeException("工单不存在: " + orderNo);
        }

        order.setResolution(resolution);
        order.setResolvedBy(resolvedBy);
        order.setResolutionTime(LocalDateTime.now());
        order.setStatus(DeviceFaultOrder.OrderStatus.COMPLETED.getValue());
        faultOrderRepository.updateById(order);

        if (DeviceFaultOrder.FaultType.OFFLINE.name().equals(order.getFaultType())) {
            Sensor sensor = sensorRepository.selectOne(
                    new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, order.getSensorId()));
            if (sensor != null && sensor.getStatus() == Sensor.Status.OFFLINE.getValue()) {
                sensor.setStatus(Sensor.Status.ONLINE.getValue());
                sensor.setLastOnlineTime(LocalDateTime.now());
                sensorRepository.updateById(sensor);
                log.info("传感器状态已恢复在线 - 传感器: {}", sensor.getSensorId());
            }
        }

        log.info("故障工单已处理完成 - 工单号: {}, 处理人: {}", orderNo, resolvedBy);
        return convertToDTO(order);
    }

    @Transactional
    public DeviceFaultOrderDTO closeFaultOrder(String orderNo) {
        DeviceFaultOrder order = faultOrderRepository.selectOne(
                new LambdaQueryWrapper<DeviceFaultOrder>().eq(DeviceFaultOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new RuntimeException("工单不存在: " + orderNo);
        }

        order.setStatus(DeviceFaultOrder.OrderStatus.CLOSED.getValue());
        faultOrderRepository.updateById(order);
        log.info("故障工单已关闭 - 工单号: {}", orderNo);
        return convertToDTO(order);
    }

    private void notifyAssignee(DeviceFaultOrder order) {
        try {
            if (order.getAssignee() == null || order.getAssignee().isEmpty()) {
                log.debug("工单未指派维修人员，跳过通知 - 工单号: {}", order.getOrderNo());
                return;
            }

            String channels = order.getNotifyChannels() != null ? order.getNotifyChannels() : defaultNotifyChannels;
            log.info("通知维修人员 - 工单号: {}, 维修人员: {}, 渠道: {}",
                    order.getOrderNo(), order.getAssignee(), channels);

            order.setNotifyStatus(1);
            order.setNotifyTime(LocalDateTime.now());
            faultOrderRepository.updateById(order);
        } catch (Exception e) {
            log.error("通知维修人员失败 - 工单号: {}, 错误: {}", order.getOrderNo(), e.getMessage(), e);
            order.setNotifyStatus(2);
            faultOrderRepository.updateById(order);
        }
    }

    private void pushToExternalWorkOrderSystem(DeviceFaultOrder order) {
        if (!faultOrderEnabled || workOrderApiUrl == null || workOrderApiUrl.isEmpty()) {
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (workOrderApiKey != null && !workOrderApiKey.isEmpty()) {
                headers.set("Authorization", "Bearer " + workOrderApiKey);
            }

            String payload = String.format(
                    "{\"orderNo\":\"%s\",\"sensorId\":\"%s\",\"faultType\":\"%s\",\"faultLevel\":\"%s\"," +
                            "\"description\":\"%s\",\"location\":\"%s\",\"zoneCode\":\"%s\",\"faultTime\":\"%s\"}",
                    order.getOrderNo(), order.getSensorId(), order.getFaultType(), order.getFaultLevel(),
                    order.getFaultDescription(), order.getLocation(), order.getZoneCode(), order.getFaultTime());

            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(workOrderApiUrl, entity, String.class);
            log.info("工单已推送到外部工单系统 - 工单号: {}", order.getOrderNo());
        } catch (Exception e) {
            log.warn("推送外部工单系统失败 - 工单号: {}, 错误: {}", order.getOrderNo(), e.getMessage());
        }
    }

    private String generateOrderNo() {
        return "FO-" + LocalDateTime.now().format(ORDER_DATE_FMT) + "-" + String.format("%04d", ORDER_SEQ.incrementAndGet() % 10000);
    }

    private DeviceFaultOrderDTO convertToDTO(DeviceFaultOrder order) {
        DeviceFaultOrderDTO dto = new DeviceFaultOrderDTO();
        dto.setId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setSensorId(order.getSensorId());
        dto.setSensorName(order.getSensorName());
        dto.setFaultType(order.getFaultType());
        dto.setFaultLevel(order.getFaultLevel());
        dto.setFaultDescription(order.getFaultDescription());
        dto.setFaultTime(order.getFaultTime() != null ? order.getFaultTime().toString() : null);
        dto.setLocation(order.getLocation());
        dto.setZoneCode(order.getZoneCode());
        dto.setAssignee(order.getAssignee());
        dto.setAssigneePhone(order.getAssigneePhone());
        dto.setStatus(order.getStatus());
        dto.setResolution(order.getResolution());
        dto.setResolutionTime(order.getResolutionTime() != null ? order.getResolutionTime().toString() : null);
        dto.setResolvedBy(order.getResolvedBy());
        dto.setNotifyChannels(order.getNotifyChannels());
        dto.setNotifyStatus(order.getNotifyStatus());
        dto.setNotifyTime(order.getNotifyTime() != null ? order.getNotifyTime().toString() : null);
        return dto;
    }
}
