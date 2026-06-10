package com.mine.safety.config;

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
}
