package com.mine.safety.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mine.safety.domain.*;
import com.mine.safety.dto.*;
import com.mine.safety.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDeviceService {

    private final SensorRepository sensorRepository;
    private final SensorCommParamRepository commParamRepository;
    private final SensorCalibrationRecordRepository calibrationRecordRepository;
    private final SensorMaintenanceRecordRepository maintenanceRecordRepository;
    private final SensorDataRepository sensorDataRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<SensorStatusDTO> getRealtimeStatus(String type, String zoneCode) {
        LambdaQueryWrapper<Sensor> wrapper = new LambdaQueryWrapper<>();
        if (type != null) {
            wrapper.eq(Sensor::getType, type);
        }
        if (zoneCode != null) {
            wrapper.eq(Sensor::getZoneCode, zoneCode);
        }

        return sensorRepository.selectList(wrapper).stream()
                .map(this::convertToStatusDTO)
                .collect(Collectors.toList());
    }

    public SensorStatusDTO getRealtimeStatusBySensorId(String sensorId) {
        Sensor sensor = sensorRepository.selectOne(
                new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, sensorId));
        if (sensor == null) {
            throw new RuntimeException("传感器不存在: " + sensorId);
        }
        return convertToStatusDTO(sensor);
    }

    public List<SensorCommParamDTO> getCommParams(String sensorId) {
        return commParamRepository.selectList(
                new LambdaQueryWrapper<SensorCommParam>().eq(SensorCommParam::getSensorId, sensorId))
                .stream()
                .map(this::convertCommParamToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public SensorCommParamDTO saveCommParam(String sensorId, SensorCommParamDTO dto) {
        SensorCommParam existing = commParamRepository.selectOne(
                new LambdaQueryWrapper<SensorCommParam>()
                        .eq(SensorCommParam::getSensorId, sensorId)
                        .eq(SensorCommParam::getParamKey, dto.getParamKey()));

        if (existing != null) {
            existing.setParamValue(dto.getParamValue());
            existing.setParamType(dto.getParamType());
            existing.setDescription(dto.getDescription());
            commParamRepository.updateById(existing);
            return convertCommParamToDTO(existing);
        } else {
            SensorCommParam param = new SensorCommParam();
            param.setSensorId(sensorId);
            param.setParamKey(dto.getParamKey());
            param.setParamValue(dto.getParamValue());
            param.setParamType(dto.getParamType());
            param.setDescription(dto.getDescription());
            commParamRepository.insert(param);
            return convertCommParamToDTO(param);
        }
    }

    @Transactional
    public void deleteCommParam(Long id) {
        commParamRepository.deleteById(id);
    }

    public List<SensorCalibrationRecordDTO> getCalibrationRecords(String sensorId) {
        return calibrationRecordRepository.selectList(
                new LambdaQueryWrapper<SensorCalibrationRecord>()
                        .eq(SensorCalibrationRecord::getSensorId, sensorId)
                        .orderByDesc(SensorCalibrationRecord::getCalibrationDate))
                .stream()
                .map(this::convertCalibrationToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public SensorCalibrationRecordDTO createCalibrationRecord(String sensorId, SensorCalibrationRecordDTO dto) {
        Sensor sensor = sensorRepository.selectOne(
                new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, sensorId));
        if (sensor == null) {
            throw new RuntimeException("传感器不存在: " + sensorId);
        }

        SensorCalibrationRecord record = new SensorCalibrationRecord();
        record.setSensorId(sensorId);
        record.setCalibrationNo(generateCalibrationNo());
        record.setCalibrationDate(dto.getCalibrationDate() != null ? LocalDate.parse(dto.getCalibrationDate(), DATE_FMT) : LocalDate.now());
        record.setCalibrationType(dto.getCalibrationType());
        record.setCalibrationResult(dto.getCalibrationResult());
        record.setCalibrationOrg(dto.getCalibrationOrg());
        record.setCalibrationPerson(dto.getCalibrationPerson());
        record.setCertificateNo(dto.getCertificateNo());
        record.setDeviationValue(dto.getDeviationValue());
        record.setDeviationUnit(dto.getDeviationUnit());
        record.setRemark(dto.getRemark());

        if (sensor.getCalibrationCycleDays() != null && sensor.getCalibrationCycleDays() > 0) {
            LocalDate nextDate = record.getCalibrationDate().plusDays(sensor.getCalibrationCycleDays());
            record.setNextCalibrationDate(nextDate);
            sensor.setLastCalibrationDate(record.getCalibrationDate());
            sensor.setNextCalibrationDate(nextDate);
            sensorRepository.updateById(sensor);
        }

        if (dto.getNextCalibrationDate() != null) {
            record.setNextCalibrationDate(LocalDate.parse(dto.getNextCalibrationDate(), DATE_FMT));
        }

        calibrationRecordRepository.insert(record);
        log.info("校验记录已创建 - 传感器: {}, 校验编号: {}", sensorId, record.getCalibrationNo());
        return convertCalibrationToDTO(record);
    }

    public List<SensorMaintenanceRecordDTO> getMaintenanceRecords(String sensorId) {
        return maintenanceRecordRepository.selectList(
                new LambdaQueryWrapper<SensorMaintenanceRecord>()
                        .eq(SensorMaintenanceRecord::getSensorId, sensorId)
                        .orderByDesc(SensorMaintenanceRecord::getMaintenanceDate))
                .stream()
                .map(this::convertMaintenanceToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public SensorMaintenanceRecordDTO createMaintenanceRecord(String sensorId, SensorMaintenanceRecordDTO dto) {
        Sensor sensor = sensorRepository.selectOne(
                new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, sensorId));
        if (sensor == null) {
            throw new RuntimeException("传感器不存在: " + sensorId);
        }

        SensorMaintenanceRecord record = new SensorMaintenanceRecord();
        record.setSensorId(sensorId);
        record.setMaintenanceNo(generateMaintenanceNo());
        record.setMaintenanceType(dto.getMaintenanceType());
        record.setMaintenanceDate(dto.getMaintenanceDate() != null ? LocalDateTime.parse(dto.getMaintenanceDate(), DATETIME_FMT) : LocalDateTime.now());
        record.setMaintenancePerson(dto.getMaintenancePerson());
        record.setMaintenanceContent(dto.getMaintenanceContent());
        record.setReplacedParts(dto.getReplacedParts());
        record.setCost(dto.getCost());
        record.setResult(dto.getResult());
        record.setRemark(dto.getRemark());

        maintenanceRecordRepository.insert(record);
        log.info("维保记录已创建 - 传感器: {}, 维保编号: {}", sensorId, record.getMaintenanceNo());
        return convertMaintenanceToDTO(record);
    }

    @Transactional
    public List<SensorDTO> batchImport(MultipartFile file) throws IOException {
        List<SensorImportDTO> importList = EasyExcel.read(file.getInputStream())
                .head(SensorImportDTO.class)
                .sheet()
                .doReadSync();

        List<SensorDTO> result = new ArrayList<>();
        int successCount = 0;
        int skipCount = 0;

        for (SensorImportDTO importDTO : importList) {
            try {
                if (importDTO.getSensorId() == null || importDTO.getSensorId().isEmpty()) {
                    log.warn("跳过无传感器ID的行");
                    skipCount++;
                    continue;
                }

                Long existingCount = sensorRepository.selectCount(
                        new LambdaQueryWrapper<Sensor>().eq(Sensor::getSensorId, importDTO.getSensorId()));
                if (existingCount > 0) {
                    log.warn("传感器ID已存在，跳过: {}", importDTO.getSensorId());
                    skipCount++;
                    continue;
                }

                Sensor sensor = new Sensor();
                sensor.setSensorId(importDTO.getSensorId());
                sensor.setName(importDTO.getName());
                sensor.setType(importDTO.getType());
                sensor.setProtocol(importDTO.getProtocol());
                sensor.setLocation(importDTO.getLocation());
                sensor.setCoordinatesX(importDTO.getCoordinatesX());
                sensor.setCoordinatesY(importDTO.getCoordinatesY());
                sensor.setCoordinatesZ(importDTO.getCoordinatesZ());
                sensor.setSamplingInterval(importDTO.getSamplingInterval() != null ? importDTO.getSamplingInterval() : 1);
                sensor.setMinValue(importDTO.getMinValue());
                sensor.setMaxValue(importDTO.getMaxValue());
                sensor.setUnit(importDTO.getUnit());
                sensor.setStatus(1);
                sensor.setWarningThreshold(importDTO.getWarningThreshold());
                sensor.setAlarmThreshold(importDTO.getAlarmThreshold());
                sensor.setPowerOffThreshold(importDTO.getPowerOffThreshold());
                sensor.setZoneCode(importDTO.getZoneCode());
                sensor.setCalibrationCycleDays(importDTO.getCalibrationCycleDays());
                sensor.setOfflineTimeoutMinutes(importDTO.getOfflineTimeoutMinutes());

                sensorRepository.insert(sensor);
                result.add(convertToSensorDTO(sensor));
                successCount++;
            } catch (Exception e) {
                log.error("导入传感器失败 - ID: {}, 错误: {}", importDTO.getSensorId(), e.getMessage());
                skipCount++;
            }
        }

        log.info("批量导入完成 - 成功: {}, 跳过: {}", successCount, skipCount);
        return result;
    }

    public void batchExport(OutputStream outputStream, String type, String zoneCode) {
        LambdaQueryWrapper<Sensor> wrapper = new LambdaQueryWrapper<>();
        if (type != null) {
            wrapper.eq(Sensor::getType, type);
        }
        if (zoneCode != null) {
            wrapper.eq(Sensor::getZoneCode, zoneCode);
        }

        List<Sensor> sensors = sensorRepository.selectList(wrapper);
        List<SensorImportDTO> exportList = sensors.stream()
                .map(this::convertToImportDTO)
                .collect(Collectors.toList());

        EasyExcel.write(outputStream, SensorImportDTO.class)
                .sheet("传感器台账")
                .doWrite(exportList);
    }

    public List<SensorDTO> getCalibrationExpiringSensors(int withinDays) {
        LocalDate threshold = LocalDate.now().plusDays(withinDays);
        List<Sensor> sensors = sensorRepository.selectList(
                new LambdaQueryWrapper<Sensor>()
                        .isNotNull(Sensor::getNextCalibrationDate)
                        .le(Sensor::getNextCalibrationDate, threshold)
                        .ne(Sensor::getStatus, Sensor.Status.FAULT.getValue()));

        return sensors.stream()
                .map(this::convertToSensorDTO)
                .collect(Collectors.toList());
    }

    private SensorStatusDTO convertToStatusDTO(Sensor sensor) {
        SensorStatusDTO dto = new SensorStatusDTO();
        dto.setSensorId(sensor.getSensorId());
        dto.setName(sensor.getName());
        dto.setType(sensor.getType());
        dto.setLocation(sensor.getLocation());
        dto.setZoneCode(sensor.getZoneCode());
        dto.setStatus(sensor.getStatus());

        switch (sensor.getStatus()) {
            case 0 -> dto.setStatusText("离线");
            case 1 -> dto.setStatusText("在线");
            case 2 -> dto.setStatusText("故障");
            default -> dto.setStatusText("未知");
        }

        dto.setBatteryLevel(sensor.getBatteryLevel() != null ? sensor.getBatteryLevel() : 100);
        if (dto.getBatteryLevel() > 60) {
            dto.setBatteryStatus("正常");
        } else if (dto.getBatteryLevel() > 20) {
            dto.setBatteryStatus("偏低");
        } else {
            dto.setBatteryStatus("不足");
        }

        dto.setSignalStrength(sensor.getSignalStrength() != null ? sensor.getSignalStrength() : 0);
        if (dto.getSignalStrength() > 70) {
            dto.setSignalQuality("强");
        } else if (dto.getSignalStrength() > 40) {
            dto.setSignalQuality("中");
        } else {
            dto.setSignalQuality("弱");
        }

        dto.setDataUploadDelay(sensor.getDataUploadDelay() != null ? sensor.getDataUploadDelay() : 0);
        if (dto.getDataUploadDelay() < 500) {
            dto.setDelayLevel("正常");
        } else if (dto.getDataUploadDelay() < 2000) {
            dto.setDelayLevel("偏高");
        } else {
            dto.setDelayLevel("过高");
        }

        dto.setLastOnlineTime(sensor.getLastOnlineTime() != null ? sensor.getLastOnlineTime().toString() : null);
        dto.setUnit(sensor.getUnit());
        return dto;
    }

    private SensorDTO convertToSensorDTO(Sensor sensor) {
        SensorDTO dto = new SensorDTO();
        dto.setSensorId(sensor.getSensorId());
        dto.setName(sensor.getName());
        dto.setType(sensor.getType());
        dto.setProtocol(sensor.getProtocol());
        dto.setLocation(sensor.getLocation());
        dto.setCoordinatesX(sensor.getCoordinatesX());
        dto.setCoordinatesY(sensor.getCoordinatesY());
        dto.setCoordinatesZ(sensor.getCoordinatesZ());
        dto.setSamplingInterval(sensor.getSamplingInterval());
        dto.setMinValue(sensor.getMinValue());
        dto.setMaxValue(sensor.getMaxValue());
        dto.setUnit(sensor.getUnit());
        dto.setStatus(sensor.getStatus());
        dto.setWarningThreshold(sensor.getWarningThreshold());
        dto.setAlarmThreshold(sensor.getAlarmThreshold());
        dto.setPowerOffThreshold(sensor.getPowerOffThreshold());
        dto.setZoneCode(sensor.getZoneCode());
        dto.setLastOnlineTime(sensor.getLastOnlineTime() != null ? sensor.getLastOnlineTime().toString() : null);
        dto.setBatteryLevel(sensor.getBatteryLevel());
        dto.setSignalStrength(sensor.getSignalStrength());
        dto.setDataUploadDelay(sensor.getDataUploadDelay());
        dto.setOfflineTimeoutMinutes(sensor.getOfflineTimeoutMinutes());
        dto.setCalibrationCycleDays(sensor.getCalibrationCycleDays());
        dto.setLastCalibrationDate(sensor.getLastCalibrationDate() != null ? sensor.getLastCalibrationDate().toString() : null);
        dto.setNextCalibrationDate(sensor.getNextCalibrationDate() != null ? sensor.getNextCalibrationDate().toString() : null);
        return dto;
    }

    private SensorCommParamDTO convertCommParamToDTO(SensorCommParam param) {
        SensorCommParamDTO dto = new SensorCommParamDTO();
        dto.setId(param.getId());
        dto.setSensorId(param.getSensorId());
        dto.setParamKey(param.getParamKey());
        dto.setParamValue(param.getParamValue());
        dto.setParamType(param.getParamType());
        dto.setDescription(param.getDescription());
        return dto;
    }

    private SensorCalibrationRecordDTO convertCalibrationToDTO(SensorCalibrationRecord record) {
        SensorCalibrationRecordDTO dto = new SensorCalibrationRecordDTO();
        dto.setId(record.getId());
        dto.setSensorId(record.getSensorId());
        dto.setCalibrationNo(record.getCalibrationNo());
        dto.setCalibrationDate(record.getCalibrationDate() != null ? record.getCalibrationDate().toString() : null);
        dto.setNextCalibrationDate(record.getNextCalibrationDate() != null ? record.getNextCalibrationDate().toString() : null);
        dto.setCalibrationType(record.getCalibrationType());
        dto.setCalibrationResult(record.getCalibrationResult());
        dto.setCalibrationOrg(record.getCalibrationOrg());
        dto.setCalibrationPerson(record.getCalibrationPerson());
        dto.setCertificateNo(record.getCertificateNo());
        dto.setDeviationValue(record.getDeviationValue());
        dto.setDeviationUnit(record.getDeviationUnit());
        dto.setRemark(record.getRemark());
        return dto;
    }

    private SensorMaintenanceRecordDTO convertMaintenanceToDTO(SensorMaintenanceRecord record) {
        SensorMaintenanceRecordDTO dto = new SensorMaintenanceRecordDTO();
        dto.setId(record.getId());
        dto.setSensorId(record.getSensorId());
        dto.setMaintenanceNo(record.getMaintenanceNo());
        dto.setMaintenanceType(record.getMaintenanceType());
        dto.setMaintenanceDate(record.getMaintenanceDate() != null ? record.getMaintenanceDate().toString() : null);
        dto.setMaintenancePerson(record.getMaintenancePerson());
        dto.setMaintenanceContent(record.getMaintenanceContent());
        dto.setReplacedParts(record.getReplacedParts());
        dto.setCost(record.getCost());
        dto.setResult(record.getResult());
        dto.setRemark(record.getRemark());
        return dto;
    }

    private SensorImportDTO convertToImportDTO(Sensor sensor) {
        SensorImportDTO dto = new SensorImportDTO();
        dto.setSensorId(sensor.getSensorId());
        dto.setName(sensor.getName());
        dto.setType(sensor.getType());
        dto.setProtocol(sensor.getProtocol());
        dto.setLocation(sensor.getLocation());
        dto.setCoordinatesX(sensor.getCoordinatesX());
        dto.setCoordinatesY(sensor.getCoordinatesY());
        dto.setCoordinatesZ(sensor.getCoordinatesZ());
        dto.setSamplingInterval(sensor.getSamplingInterval());
        dto.setMinValue(sensor.getMinValue());
        dto.setMaxValue(sensor.getMaxValue());
        dto.setUnit(sensor.getUnit());
        dto.setWarningThreshold(sensor.getWarningThreshold());
        dto.setAlarmThreshold(sensor.getAlarmThreshold());
        dto.setPowerOffThreshold(sensor.getPowerOffThreshold());
        dto.setZoneCode(sensor.getZoneCode());
        dto.setCalibrationCycleDays(sensor.getCalibrationCycleDays());
        dto.setOfflineTimeoutMinutes(sensor.getOfflineTimeoutMinutes());
        return dto;
    }

    private String generateCalibrationNo() {
        return "CAL-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + (int)(Math.random() * 10000);
    }

    private String generateMaintenanceNo() {
        return "MNT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + (int)(Math.random() * 10000);
    }
}
