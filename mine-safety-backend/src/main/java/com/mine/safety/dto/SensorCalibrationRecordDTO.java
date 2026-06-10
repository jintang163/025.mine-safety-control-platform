package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SensorCalibrationRecordDTO {

    private Long id;
    private String sensorId;
    private String calibrationNo;
    private String calibrationDate;
    private String nextCalibrationDate;
    private String calibrationType;
    private String calibrationResult;
    private String calibrationOrg;
    private String calibrationPerson;
    private String certificateNo;
    private BigDecimal deviationValue;
    private String deviationUnit;
    private String remark;
}
