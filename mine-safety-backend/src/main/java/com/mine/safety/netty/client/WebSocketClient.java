package com.mine.safety.netty.client;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.netty.client.handler.WebSocketClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class WebSocketClient {

    private final String url;
    private final Consumer<String> messageHandler;
    private final int connectTimeoutSeconds;

    private EventLoopGroup group;
    private Channel channel;
    private WebSocketClientHandler handler;
    private volatile boolean connected = false;

    public WebSocketClient(String url, Consumer<String> messageHandler) {
        this(url, messageHandler, 10);
    }

    public WebSocketClient(String url, Consumer<String> messageHandler, int connectTimeoutSeconds) {
        this.url = url;
        this.messageHandler = messageHandler;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public boolean connect() {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();
            String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
            int port = uri.getPort();

            if (port == -1) {
                if ("ws".equalsIgnoreCase(scheme)) {
                    port = 80;
                } else if ("wss".equalsIgnoreCase(scheme)) {
                    port = 443;
                }
            }

            if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
                log.error("不支持的协议: {}", scheme);
                return false;
            }

            final SslContext sslCtx = "wss".equalsIgnoreCase(scheme)
                    ? SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
                    : null;

            group = new NioEventLoopGroup();

            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                    uri, WebSocketVersion.V13, null, true,
                    new DefaultHttpHeaders(), 65536);

            handler = new WebSocketClientHandler(handshaker, messageHandler, this::onConnectionLost);

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                            }
                            p.addLast(new HttpClientCodec());
                            p.addLast(new HttpObjectAggregator(65536));
                            p.addLast(handler);
                        }
                    });

            channel = b.connect(uri.getHost(), port).sync().channel();

            boolean handshakeOk = handler.getHandshakeFuture().await(connectTimeoutSeconds, TimeUnit.SECONDS);
            if (!handshakeOk) {
                log.error("WebSocket握手超时 - URL: {}", url);
                close();
                return false;
            }

            if (!handler.getHandshakeFuture().isSuccess()) {
                log.error("WebSocket握手失败 - URL: {}", url, handler.getHandshakeFuture().cause());
                close();
                return false;
            }

            connected = true;
            log.info("WebSocket客户端连接成功 - URL: {}", url);
            return true;

        } catch (Exception e) {
            log.error("WebSocket客户端连接失败 - URL: {}", url, e);
            close();
            return false;
        }
    }

    public void subscribe(String topic) {
        if (!connected) {
            log.warn("WebSocket未连接，无法订阅主题: {}", topic);
            return;
        }
        String msg = JSON.toJSONString(Map.of(
                "action", "subscribe",
                "topic", topic
        ));
        send(msg);
        log.info("订阅主题: {}", topic);
    }

    public void unsubscribe(String topic) {
        if (!connected) {
            log.warn("WebSocket未连接，无法取消订阅主题: {}", topic);
            return;
        }
        String msg = JSON.toJSONString(Map.of(
                "action", "unsubscribe",
                "topic", topic
        ));
        send(msg);
        log.info("取消订阅主题: {}", topic);
    }

    public void ping() {
        if (!connected) {
            return;
        }
        String msg = JSON.toJSONString(Map.of(
                "action", "ping",
                "timestamp", System.currentTimeMillis()
        ));
        send(msg);
    }

    public void listTopics() {
        if (!connected) {
            return;
        }
        String msg = JSON.toJSONString(Map.of(
                "action", "list_topics"
        ));
        send(msg);
    }

    public void send(String message) {
        if (!connected || channel == null) {
            log.warn("WebSocket未连接，无法发送消息");
            return;
        }
        channel.writeAndFlush(new TextWebSocketFrame(message));
    }

    public void sendJson(Object payload) {
        send(JSON.toJSONString(payload));
    }

    public boolean isConnected() {
        return connected && channel != null && channel.isActive();
    }

    private void onConnectionLost() {
        connected = false;
        log.warn("WebSocket连接已断开 - URL: {}", url);
    }

    public void close() {
        connected = false;
        try {
            if (channel != null) {
                channel.close();
            }
        } finally {
            if (group != null) {
                group.shutdownGracefully();
            }
        }
        log.info("WebSocket客户端已关闭 - URL: {}", url);
    }

    public static void main(String[] args) throws InterruptedException {
        String serverUrl = "ws://localhost:8081/ws";

        WebSocketClient client = new WebSocketClient(serverUrl, message -> {
            System.out.println("收到消息: " + message);
        });

        if (client.connect()) {
            client.subscribe("alerts");
            client.subscribe("sensor-data");

            Thread.sleep(1000);
            client.listTopics();

            Thread.sleep(1000);
            client.ping();

            CountDownLatch latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                client.unsubscribe("alerts");
                client.unsubscribe("sensor-data");
                client.close();
                latch.countDown();
            }));

            latch.await();
        }
    }
}
