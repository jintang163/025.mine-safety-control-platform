package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.mine.safety.dto.AlertDTO;
import com.mine.safety.service.LinkageActionEngineService.ActionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePushService {

    private final RestTemplate restTemplate;

    @Value("${app.push.wechat-work.corp-id:}")
    private String wechatCorpId;

    @Value("${app.push.wechat-work.corp-secret:}")
    private String wechatCorpSecret;

    @Value("${app.push.wechat-work.agent-id:1000001}")
    private Integer wechatAgentId;

    @Value("${app.push.wechat-work.enabled:false}")
    private boolean wechatEnabled;

    @Value("${app.push.fcm.enabled:false}")
    private boolean fcmEnabled;

    @Value("${app.push.sms.enabled:false}")
    private boolean smsEnabled;

    private static final String WECHAT_TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid={corpId}&corpsecret={corpSecret}";
    private static final String WECHAT_MESSAGE_URL = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token={token}";
    private static final String FCM_URL = "https://fcm.googleapis.com/fcm/send";

    private String wechatAccessToken;
    private long wechatTokenExpireTime;

    public ActionResult pushAlertMessage(AlertDTO alert, Map<String, Object> params) {
        log.info("推送报警消息 - 报警: {}, 参数: {}", alert.getAlertNo(), params);

        try {
            List<String> channels = params != null && params.containsKey("channels")
                    ? (List<String>) params.get("channels")
                    : List.of("APP", "WECHAT_WORK");

            boolean urgent = params != null && params.containsKey("urgent")
                    && (boolean) params.get("urgent");

            Map<String, Boolean> results = new HashMap<>();

            for (String channel : channels) {
                try {
                    boolean success = switch (channel) {
                        case "WECHAT_WORK" -> pushWeChatWork(alert, urgent);
                        case "APP" -> pushAppNotification(alert, urgent);
                        case "SMS" -> pushSms(alert, urgent);
                        case "EMAIL" -> pushEmail(alert, urgent);
                        default -> false;
                    };
                    results.put(channel, success);
                } catch (Exception e) {
                    log.warn("消息推送失败 - 渠道: {}, 报警: {}", channel, alert.getAlertNo(), e);
                    results.put(channel, false);
                }
            }

            long successCount = results.values().stream().filter(v -> v).count();
            if (successCount > 0) {
                return ActionResult.success(JSON.toJSONString(Map.of(
                        "alertNo", alert.getAlertNo(),
                        "channels", results,
                        "successCount", successCount,
                        "urgent", urgent
                )));
            } else {
                return ActionResult.failure("All channels failed: " + results);
            }

        } catch (Exception e) {
            log.error("推送报警消息异常 - 报警: {}", alert.getAlertNo(), e);
            return ActionResult.failure(e.getMessage());
        }
    }

    private boolean pushWeChatWork(AlertDTO alert, boolean urgent) {
        if (!wechatEnabled || wechatCorpId.isEmpty() || wechatCorpSecret.isEmpty()) {
            log.debug("企业微信推送未启用");
            return false;
        }

        try {
            String accessToken = getWeChatAccessToken();
            if (accessToken == null) {
                return false;
            }

            Map<String, Object> message = buildWeChatMessage(alert, urgent);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(message, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    WECHAT_MESSAGE_URL, entity, Map.class, accessToken);

            Map<String, Object> body = response.getBody();
            if (body != null && "0".equals(String.valueOf(body.get("errcode")))) {
                log.info("企业微信推送成功 - 报警: {}", alert.getAlertNo());
                return true;
            } else {
                log.warn("企业微信推送失败 - 响应: {}", body);
                return false;
            }

        } catch (Exception e) {
            log.error("企业微信推送异常", e);
            return false;
        }
    }

    private String getWeChatAccessToken() {
        if (wechatAccessToken != null && System.currentTimeMillis() < wechatTokenExpireTime) {
            return wechatAccessToken;
        }

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    WECHAT_TOKEN_URL, Map.class, wechatCorpId, wechatCorpSecret);

            Map<String, Object> body = response.getBody();
            if (body != null && "0".equals(String.valueOf(body.get("errcode")))) {
                wechatAccessToken = (String) body.get("access_token");
                int expiresIn = (int) body.getOrDefault("expires_in", 7200);
                wechatTokenExpireTime = System.currentTimeMillis() + (expiresIn - 300) * 1000L;
                return wechatAccessToken;
            }
        } catch (Exception e) {
            log.error("获取企业微信Token异常", e);
        }

        return null;
    }

    private Map<String, Object> buildWeChatMessage(AlertDTO alert, boolean urgent) {
        String content = buildAlertContent(alert, urgent);

        Map<String, Object> message = new HashMap<>();
        message.put("touser", "@all");
        message.put("msgtype", "textcard");
        message.put("agentid", wechatAgentId);

        Map<String, Object> textcard = new HashMap<>();
        textcard.put("title", (urgent ? "【紧急】" : "【报警】") + alert.getLevelText());
        textcard.put("description", content);
        textcard.put("url", "http://localhost:8080/api/alerts/" + alert.getAlertNo());
        textcard.put("btntxt", "查看详情");
        message.put("textcard", textcard);

        return message;
    }

    private boolean pushAppNotification(AlertDTO alert, boolean urgent) {
        if (!fcmEnabled) {
            log.debug("APP推送未启用");
            return false;
        }

        try {
            Map<String, Object> message = buildFcmMessage(alert, urgent);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "key=YOUR_FCM_SERVER_KEY");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(message, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(FCM_URL, entity, Map.class);
            Map<String, Object> body = response.getBody();

            if (body != null && body.get("success") != null) {
                int success = (int) body.getOrDefault("success", 0);
                if (success > 0) {
                    log.info("FCM推送成功 - 报警: {}", alert.getAlertNo());
                    return true;
                }
            }

            log.warn("FCM推送失败 - 响应: {}", body);
            return false;

        } catch (Exception e) {
            log.error("FCM推送异常", e);
            return false;
        }
    }

    private Map<String, Object> buildFcmMessage(AlertDTO alert, boolean urgent) {
        Map<String, Object> message = new HashMap<>();
        message.put("to", "/topics/alert-notifications");

        Map<String, Object> notification = new HashMap<>();
        notification.put("title", (urgent ? "【紧急】" : "【报警】") + alert.getLevelText());
        notification.put("body", buildAlertContent(alert, urgent));
        notification.put("sound", urgent ? "emergency.mp3" : "default.mp3");
        message.put("notification", notification);

        Map<String, String> data = new HashMap<>();
        data.put("alertNo", alert.getAlertNo());
        data.put("sensorId", alert.getSensorId());
        data.put("level", alert.getLevel());
        data.put("urgent", String.valueOf(urgent));
        data.put("click_action", "ALERT_DETAIL");
        message.put("data", data);

        return message;
    }

    private boolean pushSms(AlertDTO alert, boolean urgent) {
        if (!smsEnabled) {
            log.debug("短信推送未启用");
            return false;
        }

        log.info("模拟短信推送 - 报警: {}, 紧急: {}", alert.getAlertNo(), urgent);
        return true;
    }

    private boolean pushEmail(AlertDTO alert, boolean urgent) {
        log.info("模拟邮件推送 - 报警: {}, 紧急: {}", alert.getAlertNo(), urgent);
        return true;
    }

    private String buildAlertContent(AlertDTO alert, boolean urgent) {
        StringBuilder sb = new StringBuilder();
        sb.append("报警编号: ").append(alert.getAlertNo()).append("\n");
        sb.append("报警级别: ").append(alert.getLevelText()).append("\n");
        sb.append("传感器: ").append(alert.getSensorName() != null ? alert.getSensorName() : alert.getSensorId()).append("\n");
        sb.append("报警值: ").append(alert.getAlertValue());
        if (alert.getUnit() != null) {
            sb.append(alert.getUnit());
        }
        sb.append("\n");
        sb.append("位置: ").append(alert.getZoneCode() != null ? alert.getZoneCode() : alert.getLocation()).append("\n");
        sb.append("描述: ").append(alert.getDescription()).append("\n");
        sb.append("时间: ").append(alert.getLastAlertTime());
        return sb.toString();
    }

    public ActionResult testWeChatPush() {
        AlertDTO testAlert = AlertDTO.builder()
                .alertNo("TEST-" + System.currentTimeMillis())
                .sensorId("TEST-001")
                .sensorType("TEST")
                .alertValue(new java.math.BigDecimal("0"))
                .level("INFO")
                .description("企业微信推送测试消息")
                .zoneCode("TEST-ZONE")
                .build();

        boolean result = pushWeChatWork(testAlert, false);
        return result ? ActionResult.success("Test message sent successfully")
                : ActionResult.failure("Test message failed");
    }
}
