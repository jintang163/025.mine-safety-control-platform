package com.mine.safety.netty;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.netty.handler.WebSocketServerHandler;
import com.mine.safety.netty.handler.HttpRequestHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketServer {

    @Value("${app.netty.websocket.port:8081}")
    private int port;

    @Value("${app.netty.websocket.path:/ws}")
    private String websocketPath;

    @Value("${app.netty.websocket.reader-idle-seconds:60}")
    private int readerIdleSeconds;

    @Value("${app.netty.websocket.writer-idle-seconds:0}")
    private int writerIdleSeconds;

    @Value("${app.netty.websocket.all-idle-seconds:0}")
    private int allIdleSeconds;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    private final ChannelGroup allChannels =
            new DefaultChannelGroup("all-websocket-channels", GlobalEventExecutor.INSTANCE);

    private final ConcurrentMap<String, ChannelGroup> topicChannels = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        try {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new IdleStateHandler(
                                    readerIdleSeconds, writerIdleSeconds, allIdleSeconds, TimeUnit.SECONDS));
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            pipeline.addLast(new WebSocketServerCompressionHandler());
                            pipeline.addLast(new HttpRequestHandler(websocketPath));
                            pipeline.addLast(new WebSocketServerProtocolHandler(
                                    websocketPath, null, true, 65536, false, true));
                            pipeline.addLast(new WebSocketServerHandler(
                                    allChannels, topicChannels, websocketPath));
                        }
                    });

            serverChannel = b.bind(port).sync().channel();
            log.info("Netty WebSocket服务启动成功 - 端口: {}, 路径: {}", port, websocketPath);
        } catch (Exception e) {
            log.error("Netty WebSocket服务启动失败", e);
            stop();
        }
    }

    @PreDestroy
    public void stop() {
        log.info("正在关闭Netty WebSocket服务...");
        try {
            if (serverChannel != null) {
                serverChannel.close();
            }
            allChannels.close().await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
        }
        log.info("Netty WebSocket服务已关闭");
    }

    public void broadcast(String message) {
        if (allChannels.isEmpty()) {
            return;
        }
        TextWebSocketFrame frame = new TextWebSocketFrame(message);
        allChannels.writeAndFlush(frame.retain());
        log.debug("广播消息到所有客户端 - 客户端数量: {}", allChannels.size());
    }

    public void sendToTopic(String topic, Object payload) {
        sendToTopic(topic, JSON.toJSONString(payload));
    }

    public void sendToTopic(String topic, String message) {
        ChannelGroup channels = topicChannels.get(topic);
        if (channels == null || channels.isEmpty()) {
            return;
        }
        String fullMessage = buildTopicMessage(topic, message);
        TextWebSocketFrame frame = new TextWebSocketFrame(fullMessage);
        channels.writeAndFlush(frame.retain());
        log.debug("推送消息到主题 {} - 客户端数量: {}", topic, channels.size());
    }

    public void sendToChannel(String channelId, Object payload) {
        sendToChannel(channelId, JSON.toJSONString(payload));
    }

    public void sendToChannel(String channelId, String message) {
        for (Channel channel : allChannels) {
            if (channel.id().asLongText().equals(channelId)) {
                channel.writeAndFlush(new TextWebSocketFrame(message));
                return;
            }
        }
    }

    private String buildTopicMessage(String topic, String payload) {
        return JSON.toJSONString(java.util.Map.of(
                "type", "topic",
                "topic", topic,
                "payload", payload,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public int getConnectedClientCount() {
        return allChannels.size();
    }

    public ConcurrentMap<String, ChannelGroup> getTopicChannels() {
        return topicChannels;
    }
}
