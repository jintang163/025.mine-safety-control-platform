package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.domain.Sensor;
import com.mine.safety.domain.ThresholdApproval;
import com.mine.safety.domain.ThresholdAudit;
import com.mine.safety.dto.*;
import com.mine.safety.repository.SensorRepository;
import com.mine.safety.repository.ThresholdApprovalRepository;
import com.mine.safety.repository.ThresholdAuditRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThresholdService {

    private final SensorRepository sensorRepository;
    private final ThresholdAuditRepository thresholdAuditRepository;
    private final ThresholdApprovalRepository thresholdApprovalRepository;
    private final StringRedisTemplate redisTemplate;
    private final WebSocketPushService webSocketPushService;

    private static final String THRESHOLD_CACHE_PREFIX = "threshold:sensor:";
    private static final long CACHE_EXPIRE_HOURS = 24;

    @PostConstruct
    public void init() {
        log.info("开始初始化阈值缓存...");
        List<Sensor> sensors = sensorRepository.findAll();
        for (Sensor sensor : sensors) {
            cacheThreshold(sensor);
        }
        log.info("阈值缓存初始化完成，共 {} 个传感器", sensors.size());
    }

    public List<ThresholdDTO> getAllThresholds() {
        return sensorRepository.findAll().stream()
                .map(this::convertToThresholdDTO)
                .collect(Collectors.toList());
    }

    public List<ThresholdDTO> getThresholdsByType(String sensorType) {
        return sensorRepository.findByType(sensorType).stream()
                .map(this::convertToThresholdDTO)
                .collect(Collectors.toList());
    }

    public ThresholdDTO getThresholdBySensorId(String sensorId) {
        String cacheKey = THRESHOLD_CACHE_PREFIX + sensorId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return JSON.parseObject(cached, ThresholdDTO.class);
            }
        } catch (Exception e) {
            log.warn("读取阈值缓存失败: {}", e.getMessage());
        }

        Sensor sensor = sensorRepository.findBySensorId(sensorId)
                .orElseThrow(() -> new RuntimeException("传感器不存在: " + sensorId));
        ThresholdDTO dto = convertToThresholdDTO(sensor);
        cacheThreshold(sensor);
        return dto;
    }

    public List<ThresholdDTO> getThresholdsByZone(String zoneCode) {
        return sensorRepository.findAll().stream()
                .filter(s -> s.getLocation() != null && s.getLocation().contains(zoneCode))
                .map(this::convertToThresholdDTO)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getMineLevelThresholdSummary() {
        List<Sensor> sensors = sensorRepository.findAll();
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalSensors", sensors.size());
        summary.put("gasSensors", sensors.stream().filter(s -> "GAS".equals(s.getType())).count());
        summary.put("dustSensors", sensors.stream().filter(s -> "DUST".equals(s.getType())).count());
        summary.put("coSensors", sensors.stream().filter(s -> "CO".equals(s.getType())).count());
        summary.put("tempSensors", sensors.stream().filter(s -> "TEMPERATURE".equals(s.getType())).count());
        summary.put("windSensors", sensors.stream().filter(s -> "WIND".equals(s.getType())).count());

        Map<String, BigDecimal> defaultThresholds = new HashMap<>();
        defaultThresholds.put("gasWarning", new BigDecimal("0.8"));
        defaultThresholds.put("gasAlarm", new BigDecimal("1.0"));
        defaultThresholds.put("gasPowerOff", new BigDecimal("1.5"));
        defaultThresholds.put("dustWarning", new BigDecimal("200"));
        defaultThresholds.put("dustAlarm", new BigDecimal("500"));
        defaultThresholds.put("dustPowerOff", new BigDecimal("1000"));
        summary.put("defaultThresholds", defaultThresholds);

        return summary;
    }

    @Transactional
    public ThresholdApprovalDTO applyThresholdChange(ThresholdApplyDTO dto) {
        Sensor sensor = sensorRepository.findBySensorId(dto.getSensorId())
                .orElseThrow(() -> new RuntimeException("传感器不存在: " + dto.getSensorId()));

        validateThresholdType(dto.getThresholdType());
        validateThresholdValue(dto.getThresholdType(), dto.getNewValue(), sensor.getType());

        List<ThresholdApproval> pending = thresholdApprovalRepository.findPendingBySensorId(
                dto.getSensorId(), ThresholdApproval.ApprovalStatus.PENDING.getValue());
        if (!pending.isEmpty()) {
            throw new RuntimeException("该传感器已有待审批的阈值调整申请");
        }

        BigDecimal oldValue = getCurrentThreshold(sensor, dto.getThresholdType());

        ThresholdApproval approval = new ThresholdApproval();
        approval.setApprovalNo(generateApprovalNo());
        approval.setSensorId(dto.getSensorId());
        approval.setThresholdType(dto.getThresholdType());
        approval.setOldValue(oldValue);
        approval.setNewValue(dto.getNewValue());
        approval.setApplicant(dto.getApplicant());
        approval.setApplyReason(dto.getApplyReason());
        approval.setStatus(ThresholdApproval.ApprovalStatus.PENDING.getValue());

        approval = thresholdApprovalRepository.save(approval);

        recordAudit(dto.getSensorId(), dto.getThresholdType(), oldValue, dto.getNewValue(),
                dto.getApplicant(), ThresholdAudit.OperationType.CREATE.name(),
                approval.getId(), dto.getApplyReason());

        log.info("阈值调整申请已提交 - 审批编号: {}, 传感器: {}, 类型: {}, 原值: {}, 新值: {}",
                approval.getApprovalNo(), dto.getSensorId(), dto.getThresholdType(),
                oldValue, dto.getNewValue());

        return convertToApprovalDTO(approval, sensor);
    }

    @Transactional
    public ThresholdApprovalDTO approveThreshold(ApprovalActionDTO dto) {
        ThresholdApproval approval = thresholdApprovalRepository.findByApprovalNo(dto.getApprovalNo())
                .orElseThrow(() -> new RuntimeException("审批记录不存在: " + dto.getApprovalNo()));

        if (!ThresholdApproval.ApprovalStatus.PENDING.getValue().equals(approval.getStatus())) {
            throw new RuntimeException("该申请已处理，无法重复审批");
        }

        Sensor sensor = sensorRepository.findBySensorId(approval.getSensorId())
                .orElseThrow(() -> new RuntimeException("传感器不存在: " + approval.getSensorId()));

        if (dto.getResult().equals(ThresholdApproval.ApprovalStatus.APPROVED.getValue())) {
            updateSensorThreshold(sensor, approval.getThresholdType(), approval.getNewValue());
            sensorRepository.save(sensor);
            cacheThreshold(sensor);

            approval.setStatus(ThresholdApproval.ApprovalStatus.APPROVED.getValue());

            recordAudit(approval.getSensorId(), approval.getThresholdType(),
                    approval.getOldValue(), approval.getNewValue(),
                    dto.getApprover(), ThresholdAudit.OperationType.APPROVE.name(),
                    approval.getId(), dto.getApproveComment());

            webSocketPushService.pushThresholdUpdate(convertToThresholdDTO(sensor));

            log.info("阈值调整已批准并生效 - 传感器: {}, 类型: {}, 新值: {}",
                    approval.getSensorId(), approval.getThresholdType(), approval.getNewValue());
        } else {
            approval.setStatus(ThresholdApproval.ApprovalStatus.REJECTED.getValue());

            recordAudit(approval.getSensorId(), approval.getThresholdType(),
                    approval.getOldValue(), approval.getNewValue(),
                    dto.getApprover(), ThresholdAudit.OperationType.REJECT.name(),
                    approval.getId(), dto.getApproveComment());

            log.info("阈值调整已拒绝 - 审批编号: {}, 原因: {}",
                    dto.getApprovalNo(), dto.getApproveComment());
        }

        approval.setApprover(dto.getApprover());
        approval.setApproveComment(dto.getApproveComment());
        approval.setApprovedAt(LocalDateTime.now());
        approval = thresholdApprovalRepository.save(approval);

        return convertToApprovalDTO(approval, sensor);
    }

    public Page<ThresholdApprovalDTO> getApprovalList(Integer status, Pageable pageable) {
        Page<ThresholdApproval> page;
        if (status != null) {
            page = thresholdApprovalRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            page = thresholdApprovalRepository.findAll(pageable);
        }
        return page.map(approval -> {
            Sensor sensor = sensorRepository.findBySensorId(approval.getSensorId()).orElse(null);
            return convertToApprovalDTO(approval, sensor);
        });
    }

    public ThresholdApprovalDTO getApprovalDetail(String approvalNo) {
        ThresholdApproval approval = thresholdApprovalRepository.findByApprovalNo(approvalNo)
                .orElseThrow(() -> new RuntimeException("审批记录不存在: " + approvalNo));
        Sensor sensor = sensorRepository.findBySensorId(approval.getSensorId()).orElse(null);
        return convertToApprovalDTO(approval, sensor);
    }

    public Page<ThresholdAuditDTO> getAuditList(String sensorId, LocalDateTime startTime,
                                                 LocalDateTime endTime, Pageable pageable) {
        List<ThresholdAudit> audits;
        if (sensorId != null) {
            if (startTime != null && endTime != null) {
                audits = thresholdAuditRepository.findBySensorIdAndTimeRange(sensorId, startTime, endTime);
            } else {
                audits = thresholdAuditRepository.findBySensorIdOrderByCreatedAtDesc(sensorId);
            }
        } else if (startTime != null && endTime != null) {
            audits = thresholdAuditRepository.findByTimeRange(startTime, endTime);
        } else {
            audits = thresholdAuditRepository.findAll();
        }
        audits.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        List<ThresholdAuditDTO> dtos = audits.stream()
                .map(this::convertToAuditDTO)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), dtos.size());
        List<ThresholdAuditDTO> pageContent = start < dtos.size() ? dtos.subList(start, end) : Collections.emptyList();

        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, dtos.size());
    }

    public Map<String, Object> getApprovalStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pendingCount", thresholdApprovalRepository.countByStatus(
                ThresholdApproval.ApprovalStatus.PENDING.getValue()));
        stats.put("approvedCount", thresholdApprovalRepository.countByStatus(
                ThresholdApproval.ApprovalStatus.APPROVED.getValue()));
        stats.put("rejectedCount", thresholdApprovalRepository.countByStatus(
                ThresholdApproval.ApprovalStatus.REJECTED.getValue()));
        return stats;
    }

    private void cacheThreshold(Sensor sensor) {
        String cacheKey = THRESHOLD_CACHE_PREFIX + sensor.getSensorId();
        ThresholdDTO dto = convertToThresholdDTO(sensor);
        try {
            redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(dto),
                    CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("缓存阈值失败: {}", e.getMessage());
        }
    }

    private BigDecimal getCurrentThreshold(Sensor sensor, String thresholdType) {
        return switch (ThresholdAudit.ThresholdType.valueOf(thresholdType)) {
            case WARNING -> sensor.getWarningThreshold();
            case ALARM -> sensor.getAlarmThreshold();
            case POWER_OFF -> sensor.getPowerOffThreshold();
        };
    }

    private void updateSensorThreshold(Sensor sensor, String thresholdType, BigDecimal newValue) {
        switch (ThresholdAudit.ThresholdType.valueOf(thresholdType)) {
            case WARNING -> sensor.setWarningThreshold(newValue);
            case ALARM -> sensor.setAlarmThreshold(newValue);
            case POWER_OFF -> sensor.setPowerOffThreshold(newValue);
        }
    }

    private void recordAudit(String sensorId, String thresholdType, BigDecimal oldValue,
                              BigDecimal newValue, String operator, String operationType,
                              Long approvalId, String reason) {
        ThresholdAudit audit = new ThresholdAudit();
        audit.setSensorId(sensorId);
        audit.setThresholdType(thresholdType);
        audit.setOldValue(oldValue);
        audit.setNewValue(newValue);
        audit.setOperator(operator);
        audit.setOperationType(operationType);
        audit.setApprovalId(approvalId);
        audit.setChangeReason(reason);
        thresholdAuditRepository.save(audit);
    }

    private void validateThresholdType(String type) {
        try {
            ThresholdAudit.ThresholdType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("无效的阈值类型: " + type);
        }
    }

    private void validateThresholdValue(String thresholdType, BigDecimal value, String sensorType) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("阈值不能为负数");
        }

        BigDecimal maxAllowed = switch (sensorType) {
            case "GAS" -> new BigDecimal("4.0");
            case "DUST" -> new BigDecimal("1000");
            case "CO" -> new BigDecimal("500");
            case "TEMPERATURE" -> new BigDecimal("100");
            case "WIND" -> new BigDecimal("15");
            default -> new BigDecimal("9999");
        };

        if (value.compareTo(maxAllowed) > 0) {
            throw new RuntimeException("阈值超过最大允许值: " + maxAllowed);
        }
    }

    private String generateApprovalNo() {
        return "APR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private ThresholdDTO convertToThresholdDTO(Sensor sensor) {
        ThresholdDTO dto = new ThresholdDTO();
        dto.setSensorId(sensor.getSensorId());
        dto.setSensorName(sensor.getName());
        dto.setSensorType(sensor.getType());
        dto.setWarningThreshold(sensor.getWarningThreshold());
        dto.setAlarmThreshold(sensor.getAlarmThreshold());
        dto.setPowerOffThreshold(sensor.getPowerOffThreshold());
        dto.setUpdatedAt(sensor.getUpdatedAt());
        return dto;
    }

    private ThresholdApprovalDTO convertToApprovalDTO(ThresholdApproval approval, Sensor sensor) {
        ThresholdApprovalDTO dto = new ThresholdApprovalDTO();
        dto.setId(approval.getId());
        dto.setApprovalNo(approval.getApprovalNo());
        dto.setSensorId(approval.getSensorId());
        dto.setThresholdType(approval.getThresholdType());
        dto.setOldValue(approval.getOldValue());
        dto.setNewValue(approval.getNewValue());
        dto.setApplicant(approval.getApplicant());
        dto.setApplyReason(approval.getApplyReason());
        dto.setStatus(approval.getStatus());
        dto.setStatusText(getApprovalStatusText(approval.getStatus()));
        dto.setApprover(approval.getApprover());
        dto.setApproveComment(approval.getApproveComment());
        dto.setApprovedAt(approval.getApprovedAt());
        dto.setCreatedAt(approval.getCreatedAt());
        if (sensor != null) {
            dto.setSensorName(sensor.getName());
            dto.setSensorType(sensor.getType());
        }
        return dto;
    }

    private ThresholdAuditDTO convertToAuditDTO(ThresholdAudit audit) {
        ThresholdAuditDTO dto = new ThresholdAuditDTO();
        dto.setId(audit.getId());
        dto.setSensorId(audit.getSensorId());
        dto.setThresholdType(audit.getThresholdType());
        dto.setThresholdTypeText(getThresholdTypeText(audit.getThresholdType()));
        dto.setOldValue(audit.getOldValue());
        dto.setNewValue(audit.getNewValue());
        dto.setOperator(audit.getOperator());
        dto.setOperationType(audit.getOperationType());
        dto.setOperationTypeText(getOperationTypeText(audit.getOperationType()));
        dto.setApprovalId(audit.getApprovalId());
        dto.setChangeReason(audit.getChangeReason());
        dto.setCreatedAt(audit.getCreatedAt());

        sensorRepository.findBySensorId(audit.getSensorId()).ifPresent(s ->
                dto.setSensorName(s.getName()));
        return dto;
    }

    private String getApprovalStatusText(Integer status) {
        return switch (ThresholdApproval.ApprovalStatus.values()[status]) {
            case PENDING -> "待审批";
            case APPROVED -> "已通过";
            case REJECTED -> "已拒绝";
        };
    }

    private String getThresholdTypeText(String type) {
        return switch (ThresholdAudit.ThresholdType.valueOf(type)) {
            case WARNING -> "预警阈值";
            case ALARM -> "报警阈值";
            case POWER_OFF -> "断电阈值";
        };
    }

    private String getOperationTypeText(String type) {
        return switch (ThresholdAudit.OperationType.valueOf(type)) {
            case CREATE -> "提交申请";
            case UPDATE -> "更新";
            case APPROVE -> "审批通过";
            case REJECT -> "审批拒绝";
        };
    }
}
