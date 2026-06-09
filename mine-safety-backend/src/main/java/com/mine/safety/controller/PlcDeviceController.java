package com.mine.safety.controller;

import com.mine.safety.domain.PlcDevice;
import com.mine.safety.dto.ResponseDTO;
import com.mine.safety.repository.PlcDeviceRepository;
import com.mine.safety.service.PlcControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    public ResponseDTO<IPage<PlcDevice>> getDevices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseDTO.success(plcDeviceRepository.selectPage(new Page<>(page, size), null));
    }

    @GetMapping("/enabled")
    public ResponseDTO<List<PlcDevice>> getEnabledDevices() {
        return ResponseDTO.success(plcDeviceRepository.selectList(
                new LambdaQueryWrapper<PlcDevice>().eq(PlcDevice::getEnabled, true)));
    }

    @GetMapping("/type/{deviceType}")
    public ResponseDTO<List<PlcDevice>> getDevicesByType(@PathVariable String deviceType) {
        return ResponseDTO.success(plcDeviceRepository.selectList(
                new LambdaQueryWrapper<PlcDevice>().eq(PlcDevice::getDeviceType, deviceType).eq(PlcDevice::getEnabled, true)));
    }

    @GetMapping("/zone/{zoneCode}")
    public ResponseDTO<List<PlcDevice>> getDevicesByZone(@PathVariable String zoneCode) {
        return ResponseDTO.success(plcDeviceRepository.selectList(
                new LambdaQueryWrapper<PlcDevice>().eq(PlcDevice::getZoneCode, zoneCode).eq(PlcDevice::getEnabled, true)));
    }

    @GetMapping("/{id}")
    public ResponseDTO<PlcDevice> getDeviceById(@PathVariable Long id) {
        PlcDevice device = plcDeviceRepository.selectById(id);
        if (device == null) {
            return ResponseDTO.error("设备不存在");
        }
        return ResponseDTO.success(device);
    }

    @GetMapping("/code/{deviceCode}")
    public ResponseDTO<PlcDevice> getDeviceByCode(@PathVariable String deviceCode) {
        PlcDevice device = plcDeviceRepository.selectOne(
                new LambdaQueryWrapper<PlcDevice>().eq(PlcDevice::getDeviceCode, deviceCode));
        if (device == null) {
            return ResponseDTO.error("设备不存在");
        }
        return ResponseDTO.success(device);
    }

    @PostMapping
    public ResponseDTO<PlcDevice> createDevice(@RequestBody PlcDevice device) {
        if (plcDeviceRepository.selectCount(new LambdaQueryWrapper<PlcDevice>().eq(PlcDevice::getDeviceCode, device.getDeviceCode())) > 0) {
            return ResponseDTO.error("设备编码已存在");
        }

        plcDeviceRepository.insert(device);
        log.info("创建PLC设备成功 - 设备: {}, 编码: {}", device.getDeviceName(), device.getDeviceCode());
        return ResponseDTO.success(device);
    }

    @PutMapping("/{id}")
    public ResponseDTO<PlcDevice> updateDevice(@PathVariable Long id, @RequestBody PlcDevice device) {
        PlcDevice existing = plcDeviceRepository.selectById(id);
        if (existing == null) {
            return ResponseDTO.error("设备不存在");
        }

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

        plcDeviceRepository.updateById(existing);
        log.info("更新PLC设备成功 - 设备: {}", existing.getDeviceCode());
        return ResponseDTO.success(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> deleteDevice(@PathVariable Long id) {
        if (plcDeviceRepository.selectById(id) == null) {
            return ResponseDTO.error("设备不存在");
        }

        plcDeviceRepository.deleteById(id);
        log.info("删除PLC设备成功 - ID: {}", id);
        return ResponseDTO.success();
    }

    @PostMapping("/{id}/test")
    public ResponseDTO<Map<String, Object>> testDeviceConnection(@PathVariable Long id) {
        PlcDevice device = plcDeviceRepository.selectById(id);
        if (device == null) {
            return ResponseDTO.error("设备不存在");
        }
        boolean connected = plcControlService.testDeviceConnection(device.getDeviceCode());
        return ResponseDTO.success(Map.of(
                "deviceCode", device.getDeviceCode(),
                "connected", connected,
                "message", connected ? "连接测试成功" : "连接测试失败"
        ));
    }

    @PostMapping("/{id}/on")
    public ResponseDTO<Map<String, Object>> turnOnDevice(@PathVariable Long id) {
        PlcDevice device = plcDeviceRepository.selectById(id);
        if (device == null) {
            return ResponseDTO.error("设备不存在");
        }
        boolean success = plcControlService.writeCoil(device, true);
        return ResponseDTO.success(Map.of(
                "deviceCode", device.getDeviceCode(),
                "success", success,
                "status", success ? "已开启" : "开启失败"
        ));
    }

    @PostMapping("/{id}/off")
    public ResponseDTO<Map<String, Object>> turnOffDevice(@PathVariable Long id) {
        PlcDevice device = plcDeviceRepository.selectById(id);
        if (device == null) {
            return ResponseDTO.error("设备不存在");
        }
        boolean success = plcControlService.writeCoil(device, false);
        return ResponseDTO.success(Map.of(
                "deviceCode", device.getDeviceCode(),
                "success", success,
                "status", success ? "已关闭" : "关闭失败"
        ));
    }

    @GetMapping("/{id}/status")
    public ResponseDTO<Map<String, Object>> getDeviceStatus(@PathVariable Long id) {
        PlcDevice device = plcDeviceRepository.selectById(id);
        if (device == null) {
            return ResponseDTO.error("设备不存在");
        }
        boolean status = plcControlService.readCoil(device);
        int value = plcControlService.readRegister(device);
        return ResponseDTO.success(Map.of(
                "deviceCode", device.getDeviceCode(),
                "coilStatus", status,
                "registerValue", value,
                "deviceStatus", device.getStatus()
        ));
    }

    @GetMapping("/statistics")
    public ResponseDTO<Map<String, Object>> getDeviceStatistics() {
        long total = plcDeviceRepository.selectCount(null);
        long enabledCount = plcDeviceRepository.selectCount(new LambdaQueryWrapper<PlcDevice>().eq(PlcDevice::getEnabled, true));
        long onlineCount = plcDeviceRepository.selectCount(new LambdaQueryWrapper<PlcDevice>().eq(PlcDevice::getStatus, 1));
        long offlineCount = plcDeviceRepository.selectCount(new LambdaQueryWrapper<PlcDevice>().eq(PlcDevice::getStatus, 0));
        long faultCount = plcDeviceRepository.selectCount(new LambdaQueryWrapper<PlcDevice>().eq(PlcDevice::getStatus, 2));

        return ResponseDTO.success(Map.of(
                "total", total,
                "enabled", enabledCount,
                "online", onlineCount,
                "offline", offlineCount,
                "fault", faultCount
        ));
    }
}
