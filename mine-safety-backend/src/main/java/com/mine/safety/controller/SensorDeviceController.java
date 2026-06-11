package com.mine.safety.controller;

import com.mine.safety.domain.MaintenanceAssigneeRule;
import com.mine.safety.dto.*;
import com.mine.safety.service.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sensor-device")
@RequiredArgsConstructor
public class SensorDeviceController {

    private final SensorDeviceService sensorDeviceService;
    private final DeviceShadowService deviceShadowService;
    private final DeviceFaultOrderService faultOrderService;
    private final MaintenanceAssigneeRuleService assigneeRuleService;

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('sensor:view')")
    public ApiResponse<List<SensorStatusDTO>> getRealtimeStatus(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String zoneCode) {
        return ApiResponse.success(sensorDeviceService.getRealtimeStatus(type, zoneCode));
    }

    @GetMapping("/status/{sensorId}")
    @PreAuthorize("hasAuthority('sensor:view')")
    public ApiResponse<SensorStatusDTO> getRealtimeStatusBySensorId(@PathVariable String sensorId) {
        return ApiResponse.success(sensorDeviceService.getRealtimeStatusBySensorId(sensorId));
    }

    @GetMapping("/{sensorId}/comm-params")
    @PreAuthorize("hasAuthority('sensor:view')")
    public ApiResponse<List<SensorCommParamDTO>> getCommParams(@PathVariable String sensorId) {
        return ApiResponse.success(sensorDeviceService.getCommParams(sensorId));
    }

    @PostMapping("/{sensorId}/comm-params")
    @PreAuthorize("hasAuthority('device:edit')")
    public ApiResponse<SensorCommParamDTO> saveCommParam(@PathVariable String sensorId,
                                                          @RequestBody SensorCommParamDTO dto) {
        return ApiResponse.success(sensorDeviceService.saveCommParam(sensorId, dto));
    }

    @DeleteMapping("/comm-params/{id}")
    @PreAuthorize("hasAuthority('device:edit')")
    public ApiResponse<Void> deleteCommParam(@PathVariable Long id) {
        sensorDeviceService.deleteCommParam(id);
        return ApiResponse.success();
    }

    @GetMapping("/{sensorId}/calibrations")
    @PreAuthorize("hasAuthority('sensor:view')")
    public ApiResponse<List<SensorCalibrationRecordDTO>> getCalibrationRecords(@PathVariable String sensorId) {
        return ApiResponse.success(sensorDeviceService.getCalibrationRecords(sensorId));
    }

    @PostMapping("/{sensorId}/calibrations")
    @PreAuthorize("hasAuthority('device:calibration')")
    public ApiResponse<SensorCalibrationRecordDTO> createCalibrationRecord(
            @PathVariable String sensorId,
            @RequestBody SensorCalibrationRecordDTO dto) {
        return ApiResponse.success(sensorDeviceService.createCalibrationRecord(sensorId, dto));
    }

    @GetMapping("/{sensorId}/maintenances")
    @PreAuthorize("hasAuthority('sensor:view')")
    public ApiResponse<List<SensorMaintenanceRecordDTO>> getMaintenanceRecords(@PathVariable String sensorId) {
        return ApiResponse.success(sensorDeviceService.getMaintenanceRecords(sensorId));
    }

    @PostMapping("/{sensorId}/maintenances")
    @PreAuthorize("hasAuthority('device:maintenance')")
    public ApiResponse<SensorMaintenanceRecordDTO> createMaintenanceRecord(
            @PathVariable String sensorId,
            @RequestBody SensorMaintenanceRecordDTO dto) {
        return ApiResponse.success(sensorDeviceService.createMaintenanceRecord(sensorId, dto));
    }

    @GetMapping("/calibration-expiring")
    @PreAuthorize("hasAuthority('sensor:view')")
    public ApiResponse<List<SensorDTO>> getCalibrationExpiringSensors(
            @RequestParam(defaultValue = "30") int withinDays) {
        return ApiResponse.success(sensorDeviceService.getCalibrationExpiringSensors(withinDays));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('sensor:edit')")
    public ApiResponse<List<SensorDTO>> batchImport(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.success(sensorDeviceService.batchImport(file));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('report:export')")
    public void batchExport(HttpServletResponse response,
                            @RequestParam(required = false) String type,
                            @RequestParam(required = false) String zoneCode) throws IOException {
        String fileName = URLEncoder.encode("传感器台账_" + System.currentTimeMillis() + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        sensorDeviceService.batchExport(response.getOutputStream(), type, zoneCode);
    }

    @GetMapping("/{sensorId}/shadow")
    @PreAuthorize("hasAuthority('device:view')")
    public ApiResponse<DeviceShadowDTO> getDeviceShadow(@PathVariable String sensorId) {
        return ApiResponse.success(deviceShadowService.getShadow(sensorId));
    }

    @PutMapping("/{sensorId}/shadow/desired")
    @PreAuthorize("hasAuthority('device:edit')")
    public ApiResponse<DeviceShadowDTO> updateDesiredState(
            @PathVariable String sensorId,
            @RequestBody Map<String, Object> desiredConfig) {
        return ApiResponse.success(deviceShadowService.updateDesiredState(sensorId, desiredConfig));
    }

    @PostMapping("/{sensorId}/ota/sampling-interval")
    @PreAuthorize("hasAuthority('device:edit')")
    public ApiResponse<DeviceShadowDTO> otaUpdateSamplingInterval(
            @PathVariable String sensorId,
            @RequestBody Map<String, Integer> body) {
        Integer interval = body.get("samplingInterval");
        if (interval == null || interval <= 0) {
            return ApiResponse.error(400, "采样间隔必须为正整数");
        }
        return ApiResponse.success(deviceShadowService.otaUpdateSamplingInterval(sensorId, interval));
    }

    @PostMapping("/{sensorId}/ota/thresholds")
    @PreAuthorize("hasAuthority('device:edit')")
    public ApiResponse<DeviceShadowDTO> otaUpdateThresholds(
            @PathVariable String sensorId,
            @RequestBody Map<String, Object> thresholds) {
        return ApiResponse.success(deviceShadowService.otaUpdateThresholds(sensorId, thresholds));
    }

    @GetMapping("/fault-orders")
    @PreAuthorize("hasAuthority('device:view')")
    public ApiResponse<List<DeviceFaultOrderDTO>> getFaultOrders(
            @RequestParam(required = false) String sensorId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String faultType) {
        return ApiResponse.success(faultOrderService.getFaultOrders(sensorId, status, faultType));
    }

    @GetMapping("/fault-orders/{orderNo}")
    @PreAuthorize("hasAuthority('device:view')")
    public ApiResponse<DeviceFaultOrderDTO> getFaultOrder(@PathVariable String orderNo) {
        return ApiResponse.success(faultOrderService.getFaultOrder(orderNo));
    }

    @PostMapping("/fault-orders")
    @PreAuthorize("hasAuthority('device:edit')")
    public ApiResponse<DeviceFaultOrderDTO> createFaultOrder(@RequestBody Map<String, String> body) {
        return ApiResponse.success(faultOrderService.createFaultOrder(
                body.get("sensorId"),
                body.get("faultType"),
                body.get("faultLevel"),
                body.get("faultDescription"),
                body.get("assignee"),
                body.get("assigneePhone")));
    }

    @PutMapping("/fault-orders/{orderNo}/assign")
    @PreAuthorize("hasAuthority('device:maintenance')")
    public ApiResponse<DeviceFaultOrderDTO> assignFaultOrder(
            @PathVariable String orderNo,
            @RequestBody Map<String, String> body) {
        return ApiResponse.success(faultOrderService.assignFaultOrder(
                orderNo, body.get("assignee"), body.get("assigneePhone")));
    }

    @PutMapping("/fault-orders/{orderNo}/resolve")
    @PreAuthorize("hasAuthority('device:maintenance')")
    public ApiResponse<DeviceFaultOrderDTO> resolveFaultOrder(
            @PathVariable String orderNo,
            @RequestBody Map<String, String> body) {
        return ApiResponse.success(faultOrderService.resolveFaultOrder(
                orderNo, body.get("resolution"), body.get("resolvedBy")));
    }

    @PutMapping("/fault-orders/{orderNo}/close")
    @PreAuthorize("hasAuthority('device:maintenance')")
    public ApiResponse<DeviceFaultOrderDTO> closeFaultOrder(@PathVariable String orderNo) {
        return ApiResponse.success(faultOrderService.closeFaultOrder(orderNo));
    }

    @GetMapping("/assignee-rules")
    @PreAuthorize("hasAuthority('device:view')")
    public ApiResponse<List<MaintenanceAssigneeRule>> getAssigneeRules() {
        return ApiResponse.success(assigneeRuleService.getAllRules());
    }

    @PostMapping("/assignee-rules")
    @PreAuthorize("hasAuthority('device:edit')")
    public ApiResponse<MaintenanceAssigneeRule> saveAssigneeRule(@RequestBody MaintenanceAssigneeRule rule) {
        return ApiResponse.success(assigneeRuleService.saveRule(rule));
    }

    @DeleteMapping("/assignee-rules/{id}")
    @PreAuthorize("hasAuthority('device:edit')")
    public ApiResponse<Void> deleteAssigneeRule(@PathVariable Long id) {
        assigneeRuleService.deleteRule(id);
        return ApiResponse.success();
    }
}
