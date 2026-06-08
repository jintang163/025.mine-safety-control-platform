package com.mine.safety.netty.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String websocketPath;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        if (req.uri().startsWith(websocketPath)) {
            ctx.fireChannelRead(req.retain());
            return;
        }

        if ("/favicon.ico".equals(req.uri())) {
            FullHttpResponse res = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            sendHttpResponse(ctx, req, res);
            return;
        }

        if ("/health".equals(req.uri()) || "/healthz".equals(req.uri())) {
            FullHttpResponse res = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.copiedBuffer("OK", CharsetUtil.UTF_8));
            res.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            HttpUtil.setContentLength(res, res.content().readableBytes());
            sendHttpResponse(ctx, req, res);
            return;
        }

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
        sendHttpResponse(ctx, req, res);
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        if (res.status().code() != 200) {
            res.content().writeBytes(res.status().toString().getBytes(CharsetUtil.UTF_8));
            HttpUtil.setContentLength(res, res.content().readableBytes());
        }

        res.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        boolean keepAlive = HttpUtil.isKeepAlive(req);
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!keepAlive) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("HTTP请求处理异常", cause);
        ctx.close();
    }
}
