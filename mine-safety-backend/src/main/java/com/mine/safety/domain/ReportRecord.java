package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("report_records")
public class ReportRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("report_no")
    private String reportNo;
    @TableField("template_id")
    private Long templateId;
    @TableField("template_code")
    private String templateCode;
    @TableField("report_name")
    private String reportName;
    @TableField("report_type")
    private String reportType;
    @TableField("start_date")
    private LocalDate startDate;
    @TableField("end_date")
    private LocalDate endDate;
    @TableField("time_dimension")
    private String timeDimension;
    @TableField("sensor_types")
    private String sensorTypes;
    @TableField("zone_code")
    private String zoneCode;
    @TableField("report_data")
    private String reportData;
    @TableField("file_format")
    private String fileFormat;
    @TableField("file_path")
    private String filePath;
    @TableField("file_size")
    private Long fileSize;
    @TableField("file_url")
    private String fileUrl;
    @TableField("generated_by")
    private String generatedBy;
    @TableField("generation_source")
    private String generationSource;
    private Integer status;
    @TableField("error_message")
    private String errorMessage;
    @TableField("email_sent")
    private Integer emailSent;
    @TableField("email_sent_time")
    private LocalDateTime emailSentTime;
    @TableField("email_recipients")
    private String emailRecipients;
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
