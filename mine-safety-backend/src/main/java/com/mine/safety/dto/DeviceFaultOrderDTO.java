package com.mine.safety.dto;

import lombok.Data;

@Data
public class DeviceFaultOrderDTO {

    private Long id;
    private String orderNo;
    private String sensorId;
    private String sensorName;
    private String faultType;
    private String faultLevel;
    private String faultDescription;
    private String faultTime;
    private String location;
    private String zoneCode;
    private String assignee;
    private String assigneePhone;
    private Integer status;
    private String resolution;
    private String resolutionTime;
    private String resolvedBy;
    private String notifyChannels;
    private Integer notifyStatus;
    private String notifyTime;
}
