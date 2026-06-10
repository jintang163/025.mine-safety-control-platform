package com.mine.safety.dto;

import lombok.Data;

@Data
public class ReportTemplateDTO {
    private Long id;
    private String templateCode;
    private String templateName;
    private String templateType;
    private String description;
    private String sensorTypes;
    private String timeDimension;
    private String contentTemplate;
    private String fileFormat;
    private Boolean enabled;
}
