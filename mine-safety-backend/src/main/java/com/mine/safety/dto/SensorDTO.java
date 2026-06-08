package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 传感器传输对象（DTO）
 * 用于传感器配置信息在各层之间的传递，包括：
 *   - 前端创建/更新传感器
 *   - 后端返回传感器列表/详情给前端
 *
 * 包含信息：
 *   - 基础属性：ID、名称、类型、通讯协议
 *   - 位置信息：安装位置、三维坐标
 *   - 采样配置：采样间隔（sampling_interval）、测量范围
 *   - 报警阈值：预警阈值、报警阈值
 *   - 状态信息：在线状态、最后在线时间、当前值
 */
@Data
public class SensorDTO {

    /**
     * 传感器唯一标识
     * 例如：GAS-001、DUST-001
     */
    private String sensorId;

    /**
     * 传感器名称
     * 例如：回风巷瓦斯传感器
     */
    private String name;

    /**
     * 传感器类型
     * GAS(瓦斯)、DUST(粉尘)、CO(一氧化碳)、TEMPERATURE(温度)、WIND(风速)
     */
    private String type;

    /**
     * 通讯协议
     * MODBUS_RTU、MODBUS_TCP、OPC_UA、CAN、WIRELESS_4G、WIRELESS_5G
     */
    private String protocol;

    /**
     * 安装位置
     * 例如：回风巷工作面A
     */
    private String location;

    /**
     * X轴坐标（经度）
     */
    private BigDecimal coordinatesX;

    /**
     * Y轴坐标（纬度）
     */
    private BigDecimal coordinatesY;

    /**
     * Z轴坐标（海拔/深度）
     */
    private BigDecimal coordinatesZ;

    /**
     * 采样间隔（秒）
     * 核心配置，决定数据采集频率：
     *   - GAS: 1秒
     *   - CO: 2秒
     *   - DUST: 5秒
     *   - TEMPERATURE: 5秒
     *   - WIND: 10秒
     */
    private Integer samplingInterval;

    /**
     * 测量范围最小值
     */
    private BigDecimal minValue;

    /**
     * 测量范围最大值
     */
    private BigDecimal maxValue;

    /**
     * 测量单位
     * 例如：% CH4、mg/m³、ppm、℃、m/s
     */
    private String unit;

    /**
     * 传感器状态
     * 0-离线，1-在线，2-故障
     */
    private Integer status;

    /**
     * 预警阈值
     * 达到此值触发WARNING级报警
     */
    private BigDecimal warningThreshold;

    /**
     * 报警阈值
     * 达到此值触发ALERT级报警
     */
    private BigDecimal alarmThreshold;

    /**
     * 当前值（仅用于返回前端展示）
     */
    private BigDecimal currentValue;

    /**
     * 最后在线时间（字符串格式，用于返回前端）
     */
    private String lastOnlineTime;
}
