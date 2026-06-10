package com.mine.safety.dto;

import lombok.Data;

import java.util.Map;

@Data
public class DeviceShadowDTO {

    private Long id;
    private String sensorId;
    private Map<String, Object> reportedState;
    private Map<String, Object> desiredState;
    private Integer reportedVersion;
    private Integer desiredVersion;
    private String lastReportedTime;
    private String lastDesiredTime;
    private String syncStatus;
    private Map<String, Object> delta;
}
