package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("report_templates")
public class ReportTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("template_code")
    private String templateCode;
    @TableField("template_name")
    private String templateName;
    @TableField("template_type")
    private String templateType;
    private String description;
    @TableField("sensor_types")
    private String sensorTypes;
    @TableField("time_dimension")
    private String timeDimension;
    @TableField("content_template")
    private String contentTemplate;
    @TableField("file_format")
    private String fileFormat;
    private Boolean enabled;
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
