package com.mine.safety.flink.source;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;

import java.util.Properties;

public class KafkaSourceFactory {

    public static KafkaSource<String> create(String bootstrapServers, String topic, String groupId) {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", bootstrapServers);
        props.setProperty("group.id", groupId);
        props.setProperty("auto.offset.reset", "latest");
        props.setProperty("enable.auto.commit", "true");
        props.setProperty("auto.commit.interval.ms", "5000");

        return KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(topic)
                .setGroupId(groupId)
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setStartingOffsets(OffsetsInitializer.latest())
                .setProperties(props)
                .build();
    }
}
