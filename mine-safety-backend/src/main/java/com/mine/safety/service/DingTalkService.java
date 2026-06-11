package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkService {

    private final RestTemplate restTemplate;

    @Value("${app.ops-alert.dingtalk.enabled:false}")
    private boolean dingtalkEnabled;

    @Value("${app.ops-alert.dingtalk.webhook-url:}")
    private String webhookUrl;

    @Value("${app.ops-alert.dingtalk.secret:}")
    private String secret;

    @Value("${app.ops-alert.dingtalk.at-mobiles:}")
    private String atMobiles;

    @Value("${app.ops-alert.dingtalk.at-all:false}")
    private boolean atAll;

    public boolean sendTextMessage(String content) {
        if (!dingtalkEnabled || webhookUrl.isEmpty()) {
            log.warn("DingTalk is not enabled, skip sending message");
            return false;
        }

        try {
            String url = buildWebhookUrl();

            DingTalkMessage message = new DingTalkMessage();
            message.setMsgtype("text");
            TextContent text = new TextContent();
            text.setContent(content);
            message.setText(text);

            At at = new At();
            at.setAtAll(atAll);
            if (atMobiles != null && !atMobiles.isEmpty()) {
                at.setAtMobiles(List.of(atMobiles.split(",")));
            }
            message.setAt(at);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(message), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("DingTalk message sent successfully");
                return true;
            } else {
                log.error("DingTalk message send failed: {}", response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to send DingTalk message", e);
            return false;
        }
    }

    public boolean sendMarkdownMessage(String title, String text) {
        if (!dingtalkEnabled || webhookUrl.isEmpty()) {
            log.warn("DingTalk is not enabled, skip sending message");
            return false;
        }

        try {
            String url = buildWebhookUrl();

            DingTalkMessage message = new DingTalkMessage();
            message.setMsgtype("markdown");
            MarkdownContent markdown = new MarkdownContent();
            markdown.setTitle(title);
            markdown.setText(text);
            message.setMarkdown(markdown);

            At at = new At();
            at.setAtAll(atAll);
            if (atMobiles != null && !atMobiles.isEmpty()) {
                at.setAtMobiles(List.of(atMobiles.split(",")));
            }
            message.setAt(at);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(message), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("DingTalk markdown message sent successfully");
                return true;
            } else {
                log.error("DingTalk markdown message send failed: {}", response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to send DingTalk markdown message", e);
            return false;
        }
    }

    private String buildWebhookUrl() throws NoSuchAlgorithmException, InvalidKeyException {
        if (secret == null || secret.isEmpty()) {
            return webhookUrl;
        }

        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = URLEncoder.encode(new String(Base64.encodeBase64(signData)), StandardCharsets.UTF_8);

        return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
    }

    @Data
    public static class DingTalkMessage {
        private String msgtype;
        private TextContent text;
        private MarkdownContent markdown;
        private At at;
    }

    @Data
    public static class TextContent {
        private String content;
    }

    @Data
    public static class MarkdownContent {
        private String title;
        private String text;
    }

    @Data
    public static class At {
        private boolean atAll;
        private List<String> atMobiles;
        private List<String> atUserIds;
    }
}
