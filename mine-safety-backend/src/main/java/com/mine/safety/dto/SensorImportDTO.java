package com.mine.safety.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SensorImportDTO {

    @ExcelProperty("传感器ID")
    private String sensorId;

    @ExcelProperty("名称")
    private String name;

    @ExcelProperty("类型")
    private String type;

    @ExcelProperty("通讯协议")
    private String protocol;

    @ExcelProperty("安装位置")
    private String location;

    @ExcelProperty("X坐标")
    private BigDecimal coordinatesX;

    @ExcelProperty("Y坐标")
    private BigDecimal coordinatesY;

    @ExcelProperty("Z坐标")
    private BigDecimal coordinatesZ;

    @ExcelProperty("采样间隔(秒)")
    private Integer samplingInterval;

    @ExcelProperty("最小值")
    private BigDecimal minValue;

    @ExcelProperty("最大值")
    private BigDecimal maxValue;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("预警阈值")
    private BigDecimal warningThreshold;

    @ExcelProperty("报警阈值")
    private BigDecimal alarmThreshold;

    @ExcelProperty("断电阈值")
    private BigDecimal powerOffThreshold;

    @ExcelProperty("区域编码")
    private String zoneCode;

    @ExcelProperty("校验周期(天)")
    private Integer calibrationCycleDays;

    @ExcelProperty("离线超时(分钟)")
    private Integer offlineTimeoutMinutes;
}
