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
@TableName("sensor_data")
public class SensorData {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("sensor_id")
    private String sensorId;

    private BigDecimal value;

    private LocalDateTime timestamp;

    private String location;

    private Integer quality = 1;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public enum Quality {
        ABNORMAL(0),
        NORMAL(1);

        private final int value;

        Quality(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
