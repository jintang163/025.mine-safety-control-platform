package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SensorMaintenanceRecordDTO {

    private Long id;
    private String sensorId;
    private String maintenanceNo;
    private String maintenanceType;
    private String maintenanceDate;
    private String maintenancePerson;
    private String maintenanceContent;
    private String replacedParts;
    private BigDecimal cost;
    private String result;
    private String remark;
}
