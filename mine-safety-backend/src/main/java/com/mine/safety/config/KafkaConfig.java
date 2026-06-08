package com.mine.safety.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka消息队列配置类
 * 配置Kafka生产者和消费者参数，实现高可靠消息传递
 *
 * Topic规划：
 *   - sensor-raw-data        原始传感器数据（MQTT直接转发）
 *   - sensor-processed-data  处理后传感器数据（去噪、异常检测后）
 *   - alarm-events           报警事件
 *
 * 可靠性设计：
 *   - 生产者：幂等性 + acks=1 + 重试3次
 *   - 消费者：手动ACK + 4并发消费 + 批量拉取
 */
@EnableKafka
@Configuration
public class KafkaConfig {

    /**
     * Kafka集群地址，多个用逗号分隔
     */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * 消费者组ID
     */
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * 创建Kafka生产者工厂
     * 配置消息可靠性和性能参数
     *
     * 关键配置：
     *   - acks=1: Leader副本写入成功即返回（在可靠性和性能间平衡）
     *   - retries=3: 发送失败重试3次
     *   - enable.idempotence=true: 启用幂等性，避免重复消息
     *   - batch.size=16384: 批量发送大小16KB
     *   - linger.ms=10: 等待10ms凑批量，提升吞吐量
     *
     * @return 生产者工厂
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "1");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * 创建KafkaTemplate
     * 用于发送消息到Kafka
     *
     * @return KafkaTemplate实例
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * 创建Kafka消费者工厂
     * 配置消费者参数，确保消息不丢不漏
     *
     * 关键配置：
     *   - enable.auto.commit=false: 手动提交偏移量，确保业务处理完成后再提交
     *   - auto.offset.reset=earliest: 新消费组从最早消息开始
     *   - max.poll.records=500: 每次最多拉取500条，提升吞吐量
     *   - max.poll.interval.ms=300000: 处理间隔5分钟，防止被误判为宕机
     *
     * @return 消费者工厂
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * 创建并发Kafka监听容器工厂
     * 配置并发消费和手动ACK模式
     *
     * 并发设计：
     *   - concurrency=4: 4个消费者线程，对应4个Topic分区
     *   - ack-mode=MANUAL: 手动确认模式，业务处理完成后调用acknowledge()
     *
     * @return 监听容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(4);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
