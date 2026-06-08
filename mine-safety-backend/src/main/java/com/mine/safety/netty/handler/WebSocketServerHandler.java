package com.mine.safety.netty.handler;

import com.alibaba.fastjson2.JSON;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@RequiredArgsConstructor
public class WebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final ChannelGroup allChannels;
    private final ConcurrentMap<String, ChannelGroup> topicChannels;
    private final String websocketPath;

    private final Set<String> subscribedTopics = new CopyOnWriteArraySet<>();

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        allChannels.add(channel);
        log.info("WebSocket客户端连接 - ChannelId: {}, 客户端IP: {}, 当前连接数: {}",
                channel.id().asShortText(), channel.remoteAddress(), allChannels.size());

        String welcomeMsg = JSON.toJSONString(Map.of(
                "type", "system",
                "event", "connected",
                "channelId", channel.id().asLongText(),
                "message", "连接成功",
                "timestamp", System.currentTimeMillis()
        ));
        channel.writeAndFlush(new TextWebSocketFrame(welcomeMsg));
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        for (String topic : subscribedTopics) {
            ChannelGroup group = topicChannels.get(topic);
            if (group != null) {
                group.remove(channel);
                if (group.isEmpty()) {
                    topicChannels.remove(topic);
                }
            }
        }
        subscribedTopics.clear();
        allChannels.remove(channel);
        log.info("WebSocket客户端断开 - ChannelId: {}, 当前连接数: {}",
                channel.id().asShortText(), allChannels.size());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (!(frame instanceof TextWebSocketFrame)) {
            log.warn("不支持的WebSocket帧类型: {}", frame.getClass().getName());
            return;
        }

        String request = ((TextWebSocketFrame) frame).text();
        log.debug("收到WebSocket消息 - ChannelId: {}, 内容: {}", ctx.channel().id().asShortText(), request);

        try {
            Map<String, Object> msg = JSON.parseObject(request);
            String action = (String) msg.get("action");

            switch (action) {
                case "subscribe" -> handleSubscribe(ctx, msg);
                case "unsubscribe" -> handleUnsubscribe(ctx, msg);
                case "ping" -> handlePing(ctx, msg);
                case "list_topics" -> handleListTopics(ctx);
                default -> handleUnknownAction(ctx, action);
            }
        } catch (Exception e) {
            log.warn("解析WebSocket消息失败: {}", request, e);
            sendError(ctx, "消息格式错误: " + e.getMessage());
        }
    }

    private void handleSubscribe(ChannelHandlerContext ctx, Map<String, Object> msg) {
        String topic = (String) msg.get("topic");
        if (topic == null || topic.trim().isEmpty()) {
            sendError(ctx, "订阅主题不能为空");
            return;
        }

        ChannelGroup group = topicChannels.computeIfAbsent(topic,
                k -> new DefaultChannelGroup("topic-" + topic, GlobalEventExecutor.INSTANCE));
        group.add(ctx.channel());
        subscribedTopics.add(topic);

        log.info("客户端订阅主题 - ChannelId: {}, Topic: {}", ctx.channel().id().asShortText(), topic);

        String response = JSON.toJSONString(Map.of(
                "type", "system",
                "event", "subscribed",
                "topic", topic,
                "message", "订阅成功",
                "timestamp", System.currentTimeMillis()
        ));
        ctx.channel().writeAndFlush(new TextWebSocketFrame(response));
    }

    private void handleUnsubscribe(ChannelHandlerContext ctx, Map<String, Object> msg) {
        String topic = (String) msg.get("topic");
        if (topic == null || topic.trim().isEmpty()) {
            sendError(ctx, "取消订阅主题不能为空");
            return;
        }

        ChannelGroup group = topicChannels.get(topic);
        if (group != null) {
            group.remove(ctx.channel());
            if (group.isEmpty()) {
                topicChannels.remove(topic);
            }
        }
        subscribedTopics.remove(topic);

        log.info("客户端取消订阅 - ChannelId: {}, Topic: {}", ctx.channel().id().asShortText(), topic);

        String response = JSON.toJSONString(Map.of(
                "type", "system",
                "event", "unsubscribed",
                "topic", topic,
                "message", "取消订阅成功",
                "timestamp", System.currentTimeMillis()
        ));
        ctx.channel().writeAndFlush(new TextWebSocketFrame(response));
    }

    private void handlePing(ChannelHandlerContext ctx, Map<String, Object> msg) {
        Object data = msg.get("data");
        String pong = JSON.toJSONString(Map.of(
                "type", "system",
                "event", "pong",
                "data", data != null ? data : "",
                "timestamp", System.currentTimeMillis()
        ));
        ctx.channel().writeAndFlush(new TextWebSocketFrame(pong));
    }

    private void handleListTopics(ChannelHandlerContext ctx) {
        String response = JSON.toJSONString(Map.of(
                "type", "system",
                "event", "topics_list",
                "subscribed", subscribedTopics,
                "all_topics", topicChannels.keySet(),
                "timestamp", System.currentTimeMillis()
        ));
        ctx.channel().writeAndFlush(new TextWebSocketFrame(response));
    }

    private void handleUnknownAction(ChannelHandlerContext ctx, String action) {
        sendError(ctx, "不支持的操作: " + action);
    }

    private void sendError(ChannelHandlerContext ctx, String message) {
        String error = JSON.toJSONString(Map.of(
                "type", "system",
                "event", "error",
                "message", message,
                "timestamp", System.currentTimeMillis()
        ));
        ctx.channel().writeAndFlush(new TextWebSocketFrame(error));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.info("WebSocket连接读超时，关闭连接 - ChannelId: {}", ctx.channel().id().asShortText());
                ctx.close();
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket处理异常 - ChannelId: {}", ctx.channel().id().asShortText(), cause);
        ctx.close();
    }
}
