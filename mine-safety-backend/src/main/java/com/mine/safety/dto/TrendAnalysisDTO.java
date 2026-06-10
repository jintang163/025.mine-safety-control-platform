package com.mine.safety.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class TrendAnalysisDTO {
    private String alertNo;
    private String ruleCode;
    private String ruleName;
    private String sensorType;
    private String zoneCode;
    private String metric;
    private String trendDirection;
    private Integer consecutivePeriods;
    private String periodUnit;
    private String startDate;
    private String endDate;
    private List<PeriodValue> periodValues;
    private String description;
    private String severity;

    @Data
    public static class PeriodValue {
        private String period;
        private BigDecimal value;
    }
}
