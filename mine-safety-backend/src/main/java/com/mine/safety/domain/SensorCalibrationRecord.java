package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sensor_calibration_records")
public class SensorCalibrationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("sensor_id")
    private String sensorId;

    @TableField("calibration_no")
    private String calibrationNo;

    @TableField("calibration_date")
    private LocalDate calibrationDate;

    @TableField("next_calibration_date")
    private LocalDate nextCalibrationDate;

    @TableField("calibration_type")
    private String calibrationType;

    @TableField("calibration_result")
    private String calibrationResult;

    @TableField("calibration_org")
    private String calibrationOrg;

    @TableField("calibration_person")
    private String calibrationPerson;

    @TableField("certificate_no")
    private String certificateNo;

    @TableField("deviation_value")
    private BigDecimal deviationValue;

    @TableField("deviation_unit")
    private String deviationUnit;

    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum CalibrationType {
        ROUTINE,
        EMERGENCY,
        RETURN_FACTORY
    }

    public enum CalibrationResult {
        QUALIFIED,
        UNQUALIFIED
    }
}
