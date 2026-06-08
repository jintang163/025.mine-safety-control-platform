package com.mine.safety.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 传感器实体类
 * 对应数据库表 sensors，存储所有传感器的配置信息
 *
 * 包含信息：
 *   - 基础属性：ID、名称、类型、通讯协议
 *   - 位置信息：安装位置、三维坐标
 *   - 采样配置：采样间隔（sampling_interval）、测量范围
 *   - 报警阈值：预警阈值、报警阈值
 *   - 状态信息：在线状态、最后在线时间
 */
@Data
@Entity
@Table(name = "sensors")
public class Sensor {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 传感器唯一标识，业务主键
     * 例如：GAS-001、DUST-001
     */
    @Column(name = "sensor_id", nullable = false, unique = true, length = 64)
    private String sensorId;

    /**
     * 传感器名称，用于展示
     * 例如：回风巷瓦斯传感器
     */
    @Column(nullable = false, length = 128)
    private String name;

    /**
     * 传感器类型
     * 枚举：GAS(瓦斯)、DUST(粉尘)、CO(一氧化碳)、TEMPERATURE(温度)、WIND(风速)
     */
    @Column(nullable = false, length = 32)
    private String type;

    /**
     * 通讯协议
     * 枚举：MODBUS_RTU、MODBUS_TCP、OPC_UA、CAN、WIRELESS_4G、WIRELESS_5G
     */
    @Column(nullable = false, length = 32)
    private String protocol;

    /**
     * 安装位置描述
     * 例如：回风巷工作面A、综采工作面C
     */
    @Column(length = 256)
    private String location;

    /**
     * X轴坐标（经度）
     */
    @Column(name = "coordinates_x", precision = 10, scale = 6)
    private BigDecimal coordinatesX;

    /**
     * Y轴坐标（纬度）
     */
    @Column(name = "coordinates_y", precision = 10, scale = 6)
    private BigDecimal coordinatesY;

    /**
     * Z轴坐标（海拔/深度）
     */
    @Column(name = "coordinates_z", precision = 10, scale = 6)
    private BigDecimal coordinatesZ;

    /**
     * 采样间隔（秒）
     * 核心配置：
     *   - GAS: 1秒
     *   - CO: 2秒
     *   - DUST: 5秒
     *   - TEMPERATURE: 5秒
     *   - WIND: 10秒
     */
    @Column(name = "sampling_interval")
    private Integer samplingInterval = 1;

    /**
     * 测量范围最小值
     */
    @Column(name = "min_value", precision = 10, scale = 4)
    private BigDecimal minValue;

    /**
     * 测量范围最大值
     */
    @Column(name = "max_value", precision = 10, scale = 4)
    private BigDecimal maxValue;

    /**
     * 测量单位
     * 例如：% CH4、mg/m³、ppm、℃、m/s
     */
    @Column(length = 16)
    private String unit;

    /**
     * 传感器状态
     * 0-离线，1-在线，2-故障
     */
    private Integer status = 1;

    /**
     * 最后在线时间
     * 用于判断传感器是否离线
     */
    @Column(name = "last_online_time")
    private LocalDateTime lastOnlineTime;

    /**
     * 预警阈值
     * 达到此值触发WARNING级报警
     */
    @Column(name = "warning_threshold", precision = 10, scale = 4)
    private BigDecimal warningThreshold;

    /**
     * 报警阈值
     * 达到此值触发ALERT级报警
     */
    @Column(name = "alarm_threshold", precision = 10, scale = 4)
    private BigDecimal alarmThreshold;

    /**
     * 创建时间（自动生成）
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间（自动更新）
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 传感器类型枚举
     */
    public enum SensorType {
        /** 瓦斯 */
        GAS,
        /** 粉尘 */
        DUST,
        /** 一氧化碳 */
        CO,
        /** 温度 */
        TEMPERATURE,
        /** 风速 */
        WIND
    }

    /**
     * 通讯协议枚举
     */
    public enum Protocol {
        /** Modbus RTU 串口协议 */
        MODBUS_RTU,
        /** Modbus TCP 网络协议 */
        MODBUS_TCP,
        /** OPC UA 工业协议 */
        OPC_UA,
        /** CAN 总线 */
        CAN,
        /** 4G 无线传输 */
        WIRELESS_4G,
        /** 5G 无线传输 */
        WIRELESS_5G
    }

    /**
     * 传感器状态枚举
     */
    public enum Status {
        /** 离线 */
        OFFLINE(0),
        /** 在线 */
        ONLINE(1),
        /** 故障 */
        FAULT(2);

        private final int value;

        Status(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
