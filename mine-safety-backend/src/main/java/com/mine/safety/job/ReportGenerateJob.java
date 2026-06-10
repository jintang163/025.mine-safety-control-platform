package com.mine.safety.job;

import com.mine.safety.service.ReportService;
import com.mine.safety.service.TrendAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReportGenerateJob implements Job {

    @Autowired
    private ReportService reportService;

    @Autowired
    private TrendAnalysisService trendAnalysisService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("开始执行定时报表生成任务");

        try {
            reportService.generateScheduledReports();
        } catch (Exception e) {
            log.error("定时报表生成失败: {}", e.getMessage(), e);
        }

        try {
            trendAnalysisService.executeTrendCheck();
        } catch (Exception e) {
            log.error("趋势分析检测失败: {}", e.getMessage(), e);
        }

        log.info("定时报表生成任务执行完成");
    }
}
