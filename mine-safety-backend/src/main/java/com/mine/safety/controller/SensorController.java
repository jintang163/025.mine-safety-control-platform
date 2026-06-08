package com.mine.safety.controller;

import com.mine.safety.dto.ApiResponse;
import com.mine.safety.dto.SensorDTO;
import com.mine.safety.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 传感器管理控制器
 * 提供传感器的CRUD REST API接口，以及传感器类型、协议的枚举查询。
 *
 * 接口列表：
 *   - GET    /sensors           查询传感器列表（支持按类型/状态过滤）
 *   - GET    /sensors/{id}      查询单个传感器详情
 *   - POST   /sensors           创建新传感器
 *   - PUT    /sensors/{id}      更新传感器信息
 *   - DELETE /sensors/{id}      删除传感器
 *   - GET    /sensors/types     获取支持的传感器类型列表
 *   - GET    /sensors/protocols 获取支持的通讯协议列表
 *
 * @author mine-safety
 * @since 1.0.0
 */
@RestController
@RequestMapping("/sensors")
@RequiredArgsConstructor
public class SensorController {

    /** 传感器服务 */
    private final SensorService sensorService;

    /**
     * 查询传感器列表
     * 支持按类型或状态过滤，如不提供过滤条件则返回所有传感器。
     *
     * @param type   传感器类型（可选）
     * @param status 状态（可选，0-离线，1-在线，2-故障）
     * @return 传感器列表
     */
    @GetMapping
    public ApiResponse<List<SensorDTO>> getAllSensors(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String zoneCode) {
        List<SensorDTO> sensors;
        if (type != null) {
            sensors = sensorService.getSensorsByType(type);
        } else if (status != null) {
            sensors = sensorService.getSensorsByStatus(status);
        } else if (zoneCode != null) {
            sensors = sensorService.getSensorsByZone(zoneCode);
        } else {
            sensors = sensorService.getAllSensors();
        }
        return ApiResponse.success(sensors);
    }

    /**
     * 查询单个传感器详情
     *
     * @param sensorId 传感器ID
     * @return 传感器详情
     */
    @GetMapping("/{sensorId}")
    public ApiResponse<SensorDTO> getSensorById(@PathVariable String sensorId) {
        return ApiResponse.success(sensorService.getSensorById(sensorId));
    }

    /**
     * 创建新传感器
     *
     * @param sensorDTO 传感器信息
     * @return 创建后的传感器
     */
    @PostMapping
    public ApiResponse<SensorDTO> createSensor(@RequestBody SensorDTO sensorDTO) {
        return ApiResponse.success(sensorService.createSensor(sensorDTO));
    }

    /**
     * 更新传感器信息
     * 支持部分字段更新（仅更新非空字段）。
     *
     * @param sensorId  传感器ID
     * @param sensorDTO 更新的传感器信息
     * @return 更新后的传感器
     */
    @PutMapping("/{sensorId}")
    public ApiResponse<SensorDTO> updateSensor(@PathVariable String sensorId,
                                               @RequestBody SensorDTO sensorDTO) {
        return ApiResponse.success(sensorService.updateSensor(sensorId, sensorDTO));
    }

    /**
     * 删除传感器
     *
     * @param sensorId 传感器ID
     * @return 空响应
     */
    @DeleteMapping("/{sensorId}")
    public ApiResponse<Void> deleteSensor(@PathVariable String sensorId) {
        sensorService.deleteSensor(sensorId);
        return ApiResponse.success();
    }

    /**
     * 获取支持的传感器类型列表
     *
     * @return 传感器类型列表（GAS/DUST/CO/TEMPERATURE/WIND）
     */
    @GetMapping("/types")
    public ApiResponse<List<String>> getSensorTypes() {
        return ApiResponse.success(List.of("GAS", "DUST", "CO", "TEMPERATURE", "WIND"));
    }

    /**
     * 获取支持的通讯协议列表
     *
     * @return 协议列表（MODBUS_RTU/MODBUS_TCP/OPC_UA/CAN/4G/5G）
     */
    @GetMapping("/protocols")
    public ApiResponse<List<String>> getProtocols() {
        return ApiResponse.success(List.of("MODBUS_RTU", "MODBUS_TCP", "OPC_UA", "CAN", "4G", "5G"));
    }
}
