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

@Data
@Configuration
public class InfluxDBConfig {

    @Value("${influxdb.url}")
    private String url;

    @Value("${influxdb.token}")
    private String token;

    @Value("${influxdb.org}")
    private String org;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.connect-timeout}")
    private int connectTimeout;

    @Value("${influxdb.read-timeout}")
    private int readTimeout;

    @Value("${influxdb.write-timeout}")
    private int writeTimeout;

    @Value("${influxdb.batch-size}")
    private int batchSize;

    @Value("${influxdb.flush-interval}")
    private int flushInterval;

    @Bean
    public InfluxDBClient influxDBClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket)
                .setConnectTimeout(connectTimeout, TimeUnit.SECONDS)
                .setReadTimeout(readTimeout, TimeUnit.SECONDS)
                .setWriteTimeout(writeTimeout, TimeUnit.SECONDS);
    }

    @Bean
    public WriteApi writeApi(InfluxDBClient client) {
        WriteOptions writeOptions = WriteOptions.builder()
                .batchSize(batchSize)
                .flushInterval(flushInterval)
                .jitterInterval(100)
                .retryInterval(1000)
                .maxRetries(5)
                .maxRetryDelay(5000)
                .exponentialBase(2)
                .build();
        return client.makeWriteApi(writeOptions);
    }
}
