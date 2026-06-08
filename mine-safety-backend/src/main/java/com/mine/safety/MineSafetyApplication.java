package com.mine.safety;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 煤矿安全生产风险管控平台 - 后端启动类
 *
 * 系统架构：
 *   - 边缘层：EdgeX Foundry 3.1 + 多协议设备接入（Modbus/OPC UA/CAN/4G/5G）
 *   - 消息层：EMQX 5.7（MQTT Broker）+ Kafka 7.6（消息缓冲）
 *   - 存储层：InfluxDB 2.7（时序数据）+ MySQL 8.0（结构化数据）+ Redis 7.2（缓存）
 *   - 应用层：Spring Boot 3.2.5 + Java 17
 *
 * 核心功能：
 *   - 多协议传感器数据接入与预处理
 *   - 实时数据监测与异常检测（Z-Score 3σ原则）
 *   - 指数平滑去噪（α=0.3）
 *   - 多级报警规则引擎（支持持续时间条件、冷却机制）
 *   - 多渠道报警通知（SMS/Email/Voice/Webhook）
 *   - 按传感器分频采集（瓦斯1s、粉尘5s、CO 2s等）
 *
 * 注解说明：
 *   - @SpringBootApplication: Spring Boot启动类注解
 *   - @EnableAsync: 启用异步方法执行
 *   - @EnableScheduling: 启用定时任务调度
 *
 * @author mine-safety
 * @since 1.0.0
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class MineSafetyApplication {

    /**
     * 应用程序入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MineSafetyApplication.class, args);
    }
}
