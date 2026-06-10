package com.mine.safety.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class HistoryStatisticsDTO {
    private String sensorId;
    private String sensorName;
    private String sensorType;
    private String location;
    private String zoneCode;
    private String unit;
    private BigDecimal maxValue;
    private BigDecimal avgValue;
    private BigDecimal minValue;
    private Long dataCount;
    private Long overWarningCount;
    private Long overAlarmCount;
    private Long overPowerOffCount;
    private BigDecimal overThresholdDurationMinutes;
    private List<TimeSeriesPoint> timeSeries;
    private String startDate;
    private String endDate;
    private String timeDimension;

    @Data
    public static class TimeSeriesPoint {
        private String time;
        private BigDecimal value;
        private BigDecimal avgValue;
        private BigDecimal maxValue;
        private BigDecimal minValue;
    }
}
