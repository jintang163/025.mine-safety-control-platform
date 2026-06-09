package com.mine.safety.service;

import com.mine.safety.domain.Alert;
import com.mine.safety.domain.Alert.AlertStatus;
import com.mine.safety.domain.Alert.EscalationLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEscalationScheduler {

    private final AlertLifecycleService alertLifecycleService;

    @Scheduled(fixedRate = 30000)
    public void checkAndEscalateAlerts() {
        try {
            List<Alert> alerts = alertLifecycleService.getAlertsForEscalation();

            for (Alert alert : alerts) {
                try {
                    EscalationLevel currentLevel = EscalationLevel.valueOf(alert.getEscalationLevel());
                    EscalationLevel nextLevel = currentLevel.next();

                    if (currentLevel != nextLevel) {
                        alertLifecycleService.escalateAlert(alert, currentLevel, nextLevel);
                        log.info("报警自动升级 - 编号: {}, {} -> {}",
                                alert.getAlertNo(), currentLevel.getValue(), nextLevel.getValue());
                    }
                } catch (Exception e) {
                    log.error("报警升级失败 - 编号: {}, 错误: {}", alert.getAlertNo(), e.getMessage());
                }
            }

            if (!alerts.isEmpty()) {
                log.debug("报警升级检查完成 - 需升级报警数: {}", alerts.size());
            }
        } catch (Exception e) {
            log.error("报警升级定时任务异常: {}", e.getMessage(), e);
        }
    }
}
