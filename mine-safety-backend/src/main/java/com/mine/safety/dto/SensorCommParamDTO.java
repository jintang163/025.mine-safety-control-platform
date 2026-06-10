package com.mine.safety.dto;

import lombok.Data;

@Data
public class SensorCommParamDTO {

    private Long id;
    private String sensorId;
    private String paramKey;
    private String paramValue;
    private String paramType;
    private String description;
}
