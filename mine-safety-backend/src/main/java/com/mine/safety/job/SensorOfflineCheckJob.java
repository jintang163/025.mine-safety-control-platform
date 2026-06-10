package com.mine.safety.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mine.safety.domain.DeviceFaultOrder;
import com.mine.safety.domain.Sensor;
import com.mine.safety.repository.SensorRepository;
import com.mine.safety.service.DeviceFaultOrderService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class SensorOfflineCheckJob implements Job {

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private DeviceFaultOrderService faultOrderService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        int defaultTimeoutMinutes = dataMap.getInt("offlineTimeoutMinutes");
        int lowBatteryThreshold = dataMap.getInt("lowBatteryThreshold");
        int calibrationExpiringDays = dataMap.getInt("calibrationExpiringDays");

        log.info("开始执行传感器离线巡检定时任务 - 默认超时: {}分钟", defaultTimeoutMinutes);

        checkOfflineSensors(defaultTimeoutMinutes);
        checkLowBatterySensors(lowBatteryThreshold);
        checkCalibrationExpiringSensors(calibrationExpiringDays);

        log.info("传感器离线巡检定时任务执行完成");
    }

    private void checkOfflineSensors(int defaultTimeoutMinutes) {
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(defaultTimeoutMinutes);

        List<Sensor> onlineSensors = sensorRepository.selectList(
                new LambdaQueryWrapper<Sensor>().eq(Sensor::getStatus, Sensor.Status.ONLINE.getValue()));

        int offlineCount = 0;
        for (Sensor sensor : onlineSensors) {
            if (sensor.getLastOnlineTime() == null || sensor.getLastOnlineTime().isBefore(timeout)) {
                int timeoutMinutes = sensor.getOfflineTimeoutMinutes() != null
                        ? sensor.getOfflineTimeoutMinutes() : defaultTimeoutMinutes;

                LocalDateTime sensorTimeout = LocalDateTime.now().minusMinutes(timeoutMinutes);
                if (sensor.getLastOnlineTime() == null || sensor.getLastOnlineTime().isBefore(sensorTimeout)) {
                    sensorRepository.updateSensorStatus(sensor.getSensorId(),
                            Sensor.Status.OFFLINE.getValue(), sensor.getLastOnlineTime());
                    log.warn("传感器已离线 - ID: {}, 名称: {}, 最后在线: {}",
                            sensor.getSensorId(), sensor.getName(), sensor.getLastOnlineTime());

                    try {
                        faultOrderService.autoCreateOfflineFaultOrder(sensor);
                    } catch (Exception e) {
                        log.error("创建离线故障工单失败 - 传感器: {}, 错误: {}", sensor.getSensorId(), e.getMessage());
                    }
                    offlineCount++;
                }
            }
        }
        log.info("离线巡检完成 - 在线传感器: {}, 新发现离线: {}", onlineSensors.size(), offlineCount);
    }

    private void checkLowBatterySensors(int lowBatteryThreshold) {
        List<Sensor> lowBatterySensors = sensorRepository.selectList(
                new LambdaQueryWrapper<Sensor>()
                        .eq(Sensor::getStatus, Sensor.Status.ONLINE.getValue())
                        .le(Sensor::getBatteryLevel, lowBatteryThreshold)
                        .isNotNull(Sensor::getBatteryLevel));

        for (Sensor sensor : lowBatterySensors) {
            try {
                faultOrderService.autoCreateLowBatteryFaultOrder(sensor);
            } catch (Exception e) {
                log.error("创建低电量故障工单失败 - 传感器: {}, 错误: {}", sensor.getSensorId(), e.getMessage());
            }
        }
        if (!lowBatterySensors.isEmpty()) {
            log.info("低电量巡检完成 - 低电量传感器: {}", lowBatterySensors.size());
        }
    }

    private void checkCalibrationExpiringSensors(int calibrationExpiringDays) {
        LocalDate expiringDate = LocalDate.now().plusDays(calibrationExpiringDays);

        List<Sensor> expiringSensors = sensorRepository.selectList(
                new LambdaQueryWrapper<Sensor>()
                        .isNotNull(Sensor::getNextCalibrationDate)
                        .le(Sensor::getNextCalibrationDate, expiringDate)
                        .ne(Sensor::getStatus, Sensor.Status.FAULT.getValue()));

        if (!expiringSensors.isEmpty()) {
            log.warn("校验即将过期的传感器: {} 台", expiringSensors.size());
            for (Sensor sensor : expiringSensors) {
                try {
                    String description = String.format("传感器[%s]校验即将过期，下次校验日期: %s",
                            sensor.getName(), sensor.getNextCalibrationDate());

                    faultOrderService.createFaultOrder(sensor.getSensorId(),
                            DeviceFaultOrder.FaultType.CALIBRATION_EXPIRED.name(),
                            DeviceFaultOrder.FaultLevel.MEDIUM.name(),
                            description, null, null);
                } catch (Exception e) {
                    log.debug("校验过期工单已存在 - 传感器: {}", sensor.getSensorId());
                }
            }
        }
    }
}
