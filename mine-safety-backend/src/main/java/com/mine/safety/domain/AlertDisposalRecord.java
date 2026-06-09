package com.mine.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("alert_disposal_records")
public class AlertDisposalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("alert_no")
    private String alertNo;

    @TableField("disposal_type")
    private String disposalType;

    @TableField("disposal_measures")
    private String disposalMeasures;

    @TableField("image_urls")
    private String imageUrls;

    @TableField("operator")
    private String operator;

    @TableField("operator_role")
    private String operatorRole;

    @TableField("recovery_value")
    private BigDecimal recoveryValue;

    @TableField("recovery_time")
    private LocalDateTime recoveryTime;

    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public enum DisposalType {
        CONFIRM,
        PROCESS,
        RECOVER,
        CLOSE
    }
}
