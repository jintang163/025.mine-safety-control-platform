package com.mine.safety.drools;

import com.mine.safety.dto.SensorDataDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleSensorData {

    private String sensorId;

    private String sensorType;

    private String zoneCode;

    private BigDecimal value;

    private String unit;

    private LocalDateTime timestamp;

    private Boolean enabled;

    private BigDecimal warningThreshold;

    private BigDecimal alarmThreshold;

    private BigDecimal powerOffThreshold;

    private List<SensorDataDTO> historyData;

    private boolean alertTriggered;

    private String alertLevel;

    private String alertRuleCode;

    private String alertDescription;

    public void addHistoryData(SensorDataDTO data) {
        if (historyData == null) {
            historyData = new ArrayList<>();
        }
        historyData.add(data);
        if (historyData.size() > 100) {
            historyData.remove(0);
        }
    }

    public BigDecimal calculateRateOfChange(int windowSeconds) {
        if (historyData == null || historyData.size() < 2) {
            return BigDecimal.ZERO;
        }

        LocalDateTime now = timestamp != null ? timestamp : LocalDateTime.now();
        LocalDateTime windowStart = now.minusSeconds(windowSeconds);

        List<SensorDataDTO> windowData = historyData.stream()
                .filter(d -> d.getTimestamp() != null && d.getTimestamp().isAfter(windowStart))
                .toList();

        if (windowData.size() < 2) {
            return BigDecimal.ZERO;
        }

        SensorDataDTO first = windowData.get(0);
        SensorDataDTO last = windowData.get(windowData.size() - 1);

        BigDecimal deltaValue = last.getValue().subtract(first.getValue());
        long deltaSeconds = java.time.Duration.between(first.getTimestamp(), last.getTimestamp()).getSeconds();

        if (deltaSeconds == 0) {
            return BigDecimal.ZERO;
        }

        return deltaValue.divide(BigDecimal.valueOf(deltaSeconds), 4, BigDecimal.ROUND_HALF_UP);
    }
}
