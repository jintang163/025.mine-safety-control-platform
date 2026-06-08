package com.mine.safety.controller;

import com.mine.safety.dto.ApiResponse;
import com.mine.safety.dto.SensorDTO;
import com.mine.safety.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sensors")
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    @GetMapping
    public ApiResponse<List<SensorDTO>> getAllSensors(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status) {
        List<SensorDTO> sensors;
        if (type != null) {
            sensors = sensorService.getSensorsByType(type);
        } else if (status != null) {
            sensors = sensorService.getSensorsByStatus(status);
        } else {
            sensors = sensorService.getAllSensors();
        }
        return ApiResponse.success(sensors);
    }

    @GetMapping("/{sensorId}")
    public ApiResponse<SensorDTO> getSensorById(@PathVariable String sensorId) {
        return ApiResponse.success(sensorService.getSensorById(sensorId));
    }

    @PostMapping
    public ApiResponse<SensorDTO> createSensor(@RequestBody SensorDTO sensorDTO) {
        return ApiResponse.success(sensorService.createSensor(sensorDTO));
    }

    @PutMapping("/{sensorId}")
    public ApiResponse<SensorDTO> updateSensor(@PathVariable String sensorId,
                                               @RequestBody SensorDTO sensorDTO) {
        return ApiResponse.success(sensorService.updateSensor(sensorId, sensorDTO));
    }

    @DeleteMapping("/{sensorId}")
    public ApiResponse<Void> deleteSensor(@PathVariable String sensorId) {
        sensorService.deleteSensor(sensorId);
        return ApiResponse.success();
    }

    @GetMapping("/types")
    public ApiResponse<List<String>> getSensorTypes() {
        return ApiResponse.success(List.of("GAS", "DUST", "CO", "TEMPERATURE", "WIND"));
    }

    @GetMapping("/protocols")
    public ApiResponse<List<String>> getProtocols() {
        return ApiResponse.success(List.of("MODBUS_RTU", "MODBUS_TCP", "OPC_UA", "CAN", "4G", "5G"));
    }
}
