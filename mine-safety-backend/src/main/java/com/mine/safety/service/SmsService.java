package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final RestTemplate restTemplate;

    @Value("${app.ops-alert.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${app.ops-alert.sms.provider:aliyun}")
    private String provider;

    @Value("${app.ops-alert.sms.access-key:}")
    private String accessKey;

    @Value("${app.ops-alert.sms.secret-key:}")
    private String secretKey;

    @Value("${app.ops-alert.sms.sign-name:}")
    private String signName;

    @Value("${app.ops-alert.sms.template-code:}")
    private String templateCode;

    @Value("${app.ops-alert.sms.phones:}")
    private String phones;

    public boolean sendSms(String phoneNumbers, String templateParam) {
        if (!smsEnabled || phoneNumbers == null || phoneNumbers.isEmpty()) {
            log.warn("SMS is not enabled or no phone numbers, skip sending");
            return false;
        }

        if ("aliyun".equalsIgnoreCase(provider)) {
            return sendAliyunSms(phoneNumbers, templateParam);
        } else {
            log.warn("Unsupported SMS provider: {}", provider);
            return false;
        }
    }

    public boolean sendSmsToOps(String content) {
        if (phones == null || phones.isEmpty()) {
            log.warn("No operation phones configured");
            return false;
        }
        Map<String, String> params = new HashMap<>();
        params.put("content", content);
        return sendSms(phones, JSON.toJSONString(params));
    }

    private boolean sendAliyunSms(String phoneNumbers, String templateParam) {
        try {
            log.info("Sending SMS to {}, content: {}", phoneNumbers, templateParam);

            log.info("SMS sent via {} provider (simulated)", provider);
            return true;

        } catch (Exception e) {
            log.error("Failed to send SMS via {}", provider, e);
            return false;
        }
    }
}
