package com.mine.safety.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ReportDataDTO {
    private String reportNo;
    private String reportName;
    private String reportType;
    private String startDate;
    private String endDate;
    private String timeDimension;
    private List<SensorReportItem> sensorItems;
    private Integer totalAlertCount;
    private BigDecimal totalOverThresholdDuration;

    @Data
    public static class SensorReportItem {
        private String sensorId;
        private String sensorName;
        private String location;
        private BigDecimal maxValue;
        private BigDecimal avgValue;
        private Long overWarningCount;
        private Long overAlarmCount;
        private Long overPowerOffCount;
        private BigDecimal overThresholdDurationMinutes;
        private java.util.List<HistoryStatisticsDTO.TimeSeriesPoint> hourlyData;
    }
}
