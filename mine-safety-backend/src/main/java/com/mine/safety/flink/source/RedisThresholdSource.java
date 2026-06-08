package com.mine.safety.flink.source;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.flink.model.ThresholdConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class RedisThresholdSource extends RichSourceFunction<ThresholdConfig> {

    private final String redisHost;
    private final int redisPort;
    private final String cachePrefix;

    private transient volatile boolean running = true;
    private transient Jedis jedis;
    private transient Thread subscribeThread;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        jedis = new Jedis(redisHost, redisPort);
        log.info("Redis阈值源已连接 - {}:{}", redisHost, redisPort);
    }

    @Override
    public void run(SourceContext<ThresholdConfig> ctx) throws Exception {
        loadAllThresholds(ctx);

        subscribeThread = new Thread(() -> subscribeThresholdUpdates(ctx));
        subscribeThread.setDaemon(true);
        subscribeThread.start();

        while (running) {
            try {
                TimeUnit.MINUTES.sleep(5);
                loadAllThresholds(ctx);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("刷新阈值配置失败，将在下一次周期重试: {}", e.getMessage());
            }
        }
    }

    private void loadAllThresholds(SourceContext<ThresholdConfig> ctx) {
        try {
            Set<String> keys = jedis.keys(cachePrefix + "*");
            for (String key : keys) {
                String value = jedis.get(key);
                if (value != null) {
                    try {
                        ThresholdConfig config = JSON.parseObject(value, ThresholdConfig.class);
                        if (config != null && config.getSensorId() != null) {
                            ctx.collect(config);
                            log.debug("加载阈值配置 - 传感器: {}", config.getSensorId());
                        }
                    } catch (Exception e) {
                        log.warn("解析阈值配置失败 - key: {}, value: {}", key, value);
                    }
                }
            }
            log.info("阈值配置全量加载完成，共 {} 个", keys.size());
        } catch (Exception e) {
            log.error("全量加载阈值配置失败: {}", e.getMessage());
        }
    }

    private void subscribeThresholdUpdates(SourceContext<ThresholdConfig> ctx) {
        try {
            JedisPubSub pubSub = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    try {
                        ThresholdConfig config = JSON.parseObject(message, ThresholdConfig.class);
                        if (config != null && config.getSensorId() != null) {
                            ctx.collect(config);
                            log.info("阈值配置已更新 - 传感器: {}", config.getSensorId());
                        }
                    } catch (Exception e) {
                        log.warn("解析阈值更新消息失败: {}", message);
                    }
                }

                @Override
                public void onPMessage(String pattern, String channel, String message) {
                    onMessage(channel, message);
                }
            };

            jedis.psubscribe(pubSub, "__keyevent@0__:set", "__keyevent@0__:del");
        } catch (Exception e) {
            log.error("订阅阈值更新失败: {}", e.getMessage());
        }
    }

    @Override
    public void cancel() {
        running = false;
        if (subscribeThread != null) {
            subscribeThread.interrupt();
        }
        if (jedis != null) {
            try {
                jedis.close();
            } catch (Exception e) {
                log.warn("关闭Redis连接失败: {}", e.getMessage());
            }
        }
        log.info("Redis阈值源已停止");
    }
}
