package com.mine.safety.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteOptions;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * InfluxDB时序数据库配置类
 * 负责与InfluxDB 2.x建立连接，配置读写参数
 *
 * 核心配置：
 *   - 连接参数：URL、Token、Org、Bucket
 *   - 超时设置：连接、读取、写入超时
 *   - 批量写入：批次大小、刷盘间隔、重试策略
 *
 * 数据模型：
 *   - Measurement: sensor_data
 *   - Tags: sensor_id, sensor_type, location
 *   - Fields: value (double), quality (integer)
 */
@Data
@Configuration
public class InfluxDBConfig {

    /**
     * InfluxDB服务地址，如：http://localhost:8086
     */
    @Value("${influxdb.url}")
    private String url;

    /**
     * 访问Token（具有读写权限）
     */
    @Value("${influxdb.token}")
    private String token;

    /**
     * 组织名称
     */
    @Value("${influxdb.org}")
    private String org;

    /**
     * 数据存储桶（Bucket）名称
     */
    @Value("${influxdb.bucket}")
    private String bucket;

    /**
     * 连接超时时间（秒）
     */
    @Value("${influxdb.connect-timeout}")
    private int connectTimeout;

    /**
     * 读取超时时间（秒）
     */
    @Value("${influxdb.read-timeout}")
    private int readTimeout;

    /**
     * 写入超时时间（秒）
     */
    @Value("${influxdb.write-timeout}")
    private int writeTimeout;

    /**
     * 批量写入大小（点数）
     * 达到此数量后自动刷盘
     */
    @Value("${influxdb.batch-size}")
    private int batchSize;

    /**
     * 刷盘间隔（毫秒）
     * 即使未达到batchSize，达到此时间也刷盘
     */
    @Value("${influxdb.flush-interval}")
    private int flushInterval;

    /**
     * 创建InfluxDB客户端Bean
     * 配置连接参数和超时设置
     *
     * @return InfluxDBClient客户端实例
     */
    @Bean
    public InfluxDBClient influxDBClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket)
                .setConnectTimeout(connectTimeout, TimeUnit.SECONDS)
                .setReadTimeout(readTimeout, TimeUnit.SECONDS)
                .setWriteTimeout(writeTimeout, TimeUnit.SECONDS);
    }

    /**
     * 创建WriteApi Bean（异步批量写入）
     * 配置批量写入参数和重试策略，提升写入性能和可靠性
     *
     * 写入策略：
     *   - 批量大小：500点
     *   - 刷盘间隔：500ms
     *   - 重试间隔：1s起，指数退避
     *   - 最大重试：5次
     *
     * @param client InfluxDB客户端
     * @return WriteApi写入API实例
     */
    @Bean
    public WriteApi writeApi(InfluxDBClient client) {
        WriteOptions writeOptions = WriteOptions.builder()
                .batchSize(batchSize)                    // 批量大小
                .flushInterval(flushInterval)            // 刷盘间隔
                .jitterInterval(100)                     // 抖动间隔，避免多个客户端同时刷盘
                .retryInterval(1000)                     // 初始重试间隔
                .maxRetries(5)                           // 最大重试次数
                .maxRetryDelay(5000)                     // 最大重试间隔
                .exponentialBase(2)                      // 指数退避基数
                .build();
        return client.makeWriteApi(writeOptions);
    }
}
