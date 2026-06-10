package com.mine.safety.config;

import com.mine.safety.job.ReportGenerateJob;
import com.mine.safety.job.SensorOfflineCheckJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuartzJobScheduler implements CommandLineRunner {

    private final Scheduler scheduler;

    @Override
    public void run(String... args) throws Exception {
        scheduleOfflineCheckJob();
        scheduleReportGenerateJob();
        scheduleTrendCheckJob();
        log.info("Quartz定时任务调度完成");
    }

    private void scheduleOfflineCheckJob() throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(SensorOfflineCheckJob.class)
                .withIdentity("sensorOfflineCheckJob", "SENSOR_GROUP")
                .usingJobData("offlineTimeoutMinutes", 10)
                .usingJobData("lowBatteryThreshold", 20)
                .usingJobData("calibrationExpiringDays", 30)
                .storeDurably()
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("sensorOfflineCheckTrigger", "SENSOR_GROUP")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(1)
                        .repeatForever()
                        .withMisfireHandlingInstructionIgnoreMisfires())
                .build();

        if (scheduler.checkExists(jobDetail.getKey())) {
            scheduler.deleteJob(jobDetail.getKey());
        }

        scheduler.scheduleJob(jobDetail, trigger);
        log.info("传感器离线巡检定时任务已注册 - 执行间隔: 1分钟");
    }

    private void scheduleReportGenerateJob() throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(ReportGenerateJob.class)
                .withIdentity("reportGenerateJob", "REPORT_GROUP")
                .storeDurably()
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("reportGenerateTrigger", "REPORT_GROUP")
                .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(8, 0)
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();

        if (scheduler.checkExists(jobDetail.getKey())) {
            scheduler.deleteJob(jobDetail.getKey());
        }

        scheduler.scheduleJob(jobDetail, trigger);
        log.info("报表生成定时任务已注册 - 每天 08:00 执行");
    }

    private void scheduleTrendCheckJob() throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(ReportGenerateJob.class)
                .withIdentity("trendCheckJob", "REPORT_GROUP")
                .storeDurably()
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("trendCheckTrigger", "REPORT_GROUP")
                .withSchedule(CronScheduleBuilder.weeklyOnDayAndHourAndMinute(
                        DateBuilder.MONDAY, 9, 0)
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();

        if (scheduler.checkExists(jobDetail.getKey())) {
            scheduler.deleteJob(jobDetail.getKey());
        }

        scheduler.scheduleJob(jobDetail, trigger);
        log.info("趋势分析定时任务已注册 - 每周一 09:00 执行");
    }
}
