package com.mine.safety.controller;

import com.mine.safety.domain.PlcDevice;
import com.mine.safety.dto.ResponseDTO;
import com.mine.safety.repository.PlcDeviceRepository;
import com.mine.safety.service.PlcControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/plc/devices")
@RequiredArgsConstructor
public class PlcDeviceController {

    private final PlcDeviceRepository plcDeviceRepository;
    private final PlcControlService plcControlService;

    @GetMapping
    public ResponseDTO<Page<PlcDevice>> getDevices(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseDTO.success(plcDeviceRepository.findAll(pageable));
    }

    @GetMapping("/enabled")
    public ResponseDTO<List<PlcDevice>> getEnabledDevices() {
        return ResponseDTO.success(plcDeviceRepository.findByEnabled(true));
    }

    @GetMapping("/type/{deviceType}")
    public ResponseDTO<List<PlcDevice>> getDevicesByType(@PathVariable String deviceType) {
        return ResponseDTO.success(plcDeviceRepository.findByDeviceTypeAndEnabled(deviceType, true));
    }

    @GetMapping("/zone/{zoneCode}")
    public ResponseDTO<List<PlcDevice>> getDevicesByZone(@PathVariable String zoneCode) {
        return ResponseDTO.success(plcDeviceRepository.findByZoneCodeAndEnabled(zoneCode, true));
    }

    @GetMapping("/{id}")
    public ResponseDTO<PlcDevice> getDeviceById(@PathVariable Long id) {
        return plcDeviceRepository.findById(id)
                .map(ResponseDTO::success)
                .orElse(ResponseDTO.error("设备不存在"));
    }

    @GetMapping("/code/{deviceCode}")
    public ResponseDTO<PlcDevice> getDeviceByCode(@PathVariable String deviceCode) {
        return plcDeviceRepository.findByDeviceCode(deviceCode)
                .map(ResponseDTO::success)
                .orElse(ResponseDTO.error("设备不存在"));
    }

    @PostMapping
    public ResponseDTO<PlcDevice> createDevice(@RequestBody PlcDevice device) {
        if (plcDeviceRepository.existsByDeviceCode(device.getDeviceCode())) {
            return ResponseDTO.error("设备编码已存在");
        }

        PlcDevice saved = plcDeviceRepository.save(device);
        log.info("创建PLC设备成功 - 设备: {}, 编码: {}", saved.getDeviceName(), saved.getDeviceCode());
        return ResponseDTO.success(saved);
    }

    @PutMapping("/{id}")
    public ResponseDTO<PlcDevice> updateDevice(@PathVariable Long id, @RequestBody PlcDevice device) {
        return plcDeviceRepository.findById(id)
                .map(existing -> {
                    existing.setDeviceName(device.getDeviceName());
                    existing.setDeviceType(device.getDeviceType());
                    existing.setProtocol(device.getProtocol());
                    existing.setIpAddress(device.getIpAddress());
                    existing.setPort(device.getPort());
                    existing.setSlaveId(device.getSlaveId());
                    existing.setRack(device.getRack());
                    existing.setSlot(device.getSlot());
                    existing.setRegisterAddress(device.getRegisterAddress());
                    existing.setRegisterType(device.getRegisterType());
                    existing.setDataType(device.getDataType());
                    existing.setZoneCode(device.getZoneCode());
                    existing.setLocation(device.getLocation());
                    existing.setOnValue(device.getOnValue());
                    existing.setOffValue(device.getOffValue());
                    existing.setEnabled(device.getEnabled());
                    existing.setDescription(device.getDescription());

                    PlcDevice saved = plcDeviceRepository.save(existing);
                    log.info("更新PLC设备成功 - 设备: {}", saved.getDeviceCode());
                    return ResponseDTO.success(saved);
                })
                .orElse(ResponseDTO.error("设备不存在"));
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> deleteDevice(@PathVariable Long id) {
        if (!plcDeviceRepository.existsById(id)) {
            return ResponseDTO.error("设备不存在");
        }

        plcDeviceRepository.deleteById(id);
        log.info("删除PLC设备成功 - ID: {}", id);
        return ResponseDTO.success();
    }

    @PostMapping("/{id}/test")
    public ResponseDTO<Map<String, Object>> testDeviceConnection(@PathVariable Long id) {
        return plcDeviceRepository.findById(id)
                .map(device -> {
                    boolean connected = plcControlService.testDeviceConnection(device.getDeviceCode());
                    return ResponseDTO.success(Map.of(
                            "deviceCode", device.getDeviceCode(),
                            "connected", connected,
                            "message", connected ? "连接测试成功" : "连接测试失败"
                    ));
                })
                .orElse(ResponseDTO.error("设备不存在"));
    }

    @PostMapping("/{id}/on")
    public ResponseDTO<Map<String, Object>> turnOnDevice(@PathVariable Long id) {
        return plcDeviceRepository.findById(id)
                .map(device -> {
                    boolean success = plcControlService.writeCoil(device, true);
                    return ResponseDTO.success(Map.of(
                            "deviceCode", device.getDeviceCode(),
                            "success", success,
                            "status", success ? "已开启" : "开启失败"
                    ));
                })
                .orElse(ResponseDTO.error("设备不存在"));
    }

    @PostMapping("/{id}/off")
    public ResponseDTO<Map<String, Object>> turnOffDevice(@PathVariable Long id) {
        return plcDeviceRepository.findById(id)
                .map(device -> {
                    boolean success = plcControlService.writeCoil(device, false);
                    return ResponseDTO.success(Map.of(
                            "deviceCode", device.getDeviceCode(),
                            "success", success,
                            "status", success ? "已关闭" : "关闭失败"
                    ));
                })
                .orElse(ResponseDTO.error("设备不存在"));
    }

    @GetMapping("/{id}/status")
    public ResponseDTO<Map<String, Object>> getDeviceStatus(@PathVariable Long id) {
        return plcDeviceRepository.findById(id)
                .map(device -> {
                    boolean status = plcControlService.readCoil(device);
                    int value = plcControlService.readRegister(device);
                    return ResponseDTO.success(Map.of(
                            "deviceCode", device.getDeviceCode(),
                            "coilStatus", status,
                            "registerValue", value,
                            "deviceStatus", device.getStatus()
                    ));
                })
                .orElse(ResponseDTO.error("设备不存在"));
    }

    @GetMapping("/statistics")
    public ResponseDTO<Map<String, Object>> getDeviceStatistics() {
        long total = plcDeviceRepository.count();
        long enabledCount = plcDeviceRepository.findByEnabled(true).size();
        long onlineCount = plcDeviceRepository.findByStatus(1).size();
        long offlineCount = plcDeviceRepository.findByStatus(0).size();
        long faultCount = plcDeviceRepository.findByStatus(2).size();

        return ResponseDTO.success(Map.of(
                "total", total,
                "enabled", enabledCount,
                "online", onlineCount,
                "offline", offlineCount,
                "fault", faultCount
        ));
    }
}
