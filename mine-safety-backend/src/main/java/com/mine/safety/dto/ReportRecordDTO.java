package com.mine.safety.dto;

import lombok.Data;

@Data
public class ReportRecordDTO {
    private Long id;
    private String reportNo;
    private Long templateId;
    private String templateCode;
    private String reportName;
    private String reportType;
    private String startDate;
    private String endDate;
    private String timeDimension;
    private String sensorTypes;
    private String zoneCode;
    private String fileFormat;
    private String filePath;
    private Long fileSize;
    private String fileUrl;
    private String generatedBy;
    private String generationSource;
    private Integer status;
    private String errorMessage;
    private Integer emailSent;
    private String emailSentTime;
    private String emailRecipients;
    private String createdAt;
}
