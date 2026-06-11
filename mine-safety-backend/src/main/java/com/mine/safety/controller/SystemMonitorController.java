package com.mine.safety.controller;

import com.mine.safety.dto.ApiResponse;
import com.mine.safety.service.OpsAlertService;
import com.mine.safety.service.SystemMetricsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemMonitorController {

    private final SystemMetricsService systemMetricsService;
    private final OpsAlertService opsAlertService;

    @GetMapping("/health")
    @PreAuthorize("hasAuthority('system:monitor')")
    public ApiResponse<Map<String, Object>> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");

        Map<String, Object> components = new HashMap<>();

        Map<String, Object> mqtt = new HashMap<>();
        mqtt.put("status", systemMetricsService.isMqttConnected() ? "UP" : "DOWN");
        mqtt.put("connected", systemMetricsService.isMqttConnected());
        components.put("mqtt", mqtt);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("sensorDataDelayMs", systemMetricsService.getSensorDataDelayMs());
        components.put("metrics", metrics);

        health.put("components", components);

        return ApiResponse.success(health);
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('system:monitor')")
    public ApiResponse<SystemStatusDTO> getSystemStatus() {
        SystemStatusDTO status = new SystemStatusDTO();

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        status.setCpuLoad(osBean.getProcessCpuLoad() * 100);
        status.setSystemCpuLoad(osBean.getSystemLoadAverage());

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        status.setJvmTotalMemory(totalMemory);
        status.setJvmUsedMemory(usedMemory);
        status.setJvmFreeMemory(freeMemory);
        status.setJvmMemoryUsage((double) usedMemory / totalMemory * 100);

        status.setHeapMemoryUsed(memoryBean.getHeapMemoryUsage().getUsed());
        status.setHeapMemoryMax(memoryBean.getHeapMemoryUsage().getMax());
        status.setNonHeapMemoryUsed(memoryBean.getNonHeapMemoryUsage().getUsed());

        status.setMqttConnected(systemMetricsService.isMqttConnected());
        status.setSensorDataDelayMs(systemMetricsService.getSensorDataDelayMs());

        status.setMqttDisconnectedAlert(opsAlertService.isMqttDisconnectedAlertSent());
        status.setDataDelayAlert(opsAlertService.isDataDelayAlertSent());

        return ApiResponse.success(status);
    }

    @PostMapping("/ops-alert/test")
    @PreAuthorize("hasAuthority('system:config')")
    public ApiResponse<Void> testOpsAlert(@RequestParam String title,
                                          @RequestParam String content,
                                          @RequestParam(defaultValue = "WARNING") String level) {
        opsAlertService.triggerCustomAlert(title, content, level);
        return ApiResponse.success();
    }

    @Data
    public static class SystemStatusDTO {
        private double cpuLoad;
        private double systemCpuLoad;
        private long jvmTotalMemory;
        private long jvmUsedMemory;
        private long jvmFreeMemory;
        private double jvmMemoryUsage;
        private long heapMemoryUsed;
        private long heapMemoryMax;
        private long nonHeapMemoryUsed;
        private boolean mqttConnected;
        private long sensorDataDelayMs;
        private boolean mqttDisconnectedAlert;
        private boolean dataDelayAlert;
    }
}
