package com.mine.safety.job;

import com.mine.safety.service.DeviceFaultOrderService;
import com.mine.safety.service.ReportService;
import com.mine.safety.service.TrendAnalysisService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class XxlJobHandlers {

    private final DeviceFaultOrderService deviceFaultOrderService;
    private final ReportService reportService;
    private final TrendAnalysisService trendAnalysisService;

    @XxlJob("sensorOfflineCheckHandler")
    public void sensorOfflineCheckHandler() {
        String param = XxlJobHelper.getJobParam();
        log.info("XXL-JOB: 传感器离线巡检开始执行, param={}", param);
        try {
            int offlineTimeoutMinutes = 10;
            int lowBatteryThreshold = 20;
            int calibrationExpiringDays = 30;

            if (param != null && !param.isBlank()) {
                String[] parts = param.split(",");
                try {
                    if (parts.length >= 1) offlineTimeoutMinutes = Integer.parseInt(parts[0].trim());
                    if (parts.length >= 2) lowBatteryThreshold = Integer.parseInt(parts[1].trim());
                    if (parts.length >= 3) calibrationExpiringDays = Integer.parseInt(parts[2].trim());
                } catch (NumberFormatException ignored) {
                }
            }

            int processed = deviceFaultOrderService.checkAndProcessOfflineSensors(
                    offlineTimeoutMinutes, lowBatteryThreshold, calibrationExpiringDays);
            XxlJobHelper.handleSuccess("处理完成，共处理传感器异常: " + processed + " 个");
            log.info("XXL-JOB: 传感器离线巡检执行完成, 处理数量={}", processed);
        } catch (Exception e) {
            log.error("XXL-JOB: 传感器离线巡检执行失败", e);
            XxlJobHelper.handleFail("执行失败: " + e.getMessage());
        }
    }

    @XxlJob("reportGenerateHandler")
    public void reportGenerateHandler() {
        String param = XxlJobHelper.getJobParam();
        log.info("XXL-JOB: 报表生成开始执行, param={}", param);
        try {
            int count = reportService.generateScheduledReports();
            XxlJobHelper.handleSuccess("报表生成完成，共生成: " + count + " 份");
            log.info("XXL-JOB: 报表生成执行完成, 生成数量={}", count);
        } catch (Exception e) {
            log.error("XXL-JOB: 报表生成执行失败", e);
            XxlJobHelper.handleFail("执行失败: " + e.getMessage());
        }
    }

    @XxlJob("trendCheckHandler")
    public void trendCheckHandler() {
        String param = XxlJobHelper.getJobParam();
        log.info("XXL-JOB: 趋势分析开始执行, param={}", param);
        try {
            trendAnalysisService.executeTrendCheck();
            XxlJobHelper.handleSuccess("趋势分析执行完成");
            log.info("XXL-JOB: 趋势分析执行完成");
        } catch (Exception e) {
            log.error("XXL-JOB: 趋势分析执行失败", e);
            XxlJobHelper.handleFail("执行失败: " + e.getMessage());
        }
    }
}
