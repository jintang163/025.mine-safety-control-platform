package com.mine.safety.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 传感器数据传输对象（DTO）
 * 用于传感器数据在各层之间的传递，包括：
 *   - EdgeX网关 -> MQTT -> 后端
 *   - 后端内部各服务之间
 *   - 后端 -> 前端API响应
 *
 * 数据格式（JSON示例）：
 * {
 *   "sensorId": "GAS-001",
 *   "value": 0.85,
 *   "timestamp": "2024-01-15T10:30:00",
 *   "location": "回风巷工作面A",
 *   "coordinatesX": 116.5,
 *   "coordinatesY": 39.8,
 *   "coordinatesZ": 520.5,
 *   "unit": "% CH4",
 *   "sensorType": "GAS",
 *   "quality": 1,
 *   "protocol": "MODBUS_RTU"
 * }
 */
@Data
public class SensorDataDTO {

    /**
     * 传感器ID
     * 例如：GAS-001、DUST-001
     */
    private String sensorId;

    /**
     * 采集数值
     */
    private BigDecimal value;

    /**
     * 采集时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 安装位置
     * 例如：回风巷工作面A
     */
    private String location;

    /**
     * X轴坐标（经度）
     */
    private Double coordinatesX;

    /**
     * Y轴坐标（纬度）
     */
    private Double coordinatesY;

    /**
     * Z轴坐标（海拔/深度）
     */
    private Double coordinatesZ;

    /**
     * 测量单位
     * 例如：% CH4、mg/m³、ppm、℃、m/s
     */
    private String unit;

    /**
     * 传感器类型
     * GAS、DUST、CO、TEMPERATURE、WIND
     */
    private String sensorType;

    /**
     * 数据质量
     * 0-异常，1-正常
     */
    private Integer quality = 1;

    /**
     * 通讯协议
     * MODBUS_RTU、MODBUS_TCP、OPC_UA、CAN、WIRELESS_4G、WIRELESS_5G
     */
    private String protocol;

    /**
     * 网关ID（边缘网关标识）
     */
    private String gatewayId;

    /**
     * 边缘节点ID
     */
    private Long edgeNodeId;
}
