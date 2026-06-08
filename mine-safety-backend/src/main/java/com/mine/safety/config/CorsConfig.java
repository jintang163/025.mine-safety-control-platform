package com.mine.safety.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域资源共享（CORS）配置类
 * 允许前端应用从不同域名访问后端API
 *
 * 安全注意：
 *   生产环境建议将allowedOriginPatterns替换为具体的域名白名单
 *   例如：.allowedOriginPatterns("https://*.mine-safety.com")
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 配置跨域映射规则
     *
     * @param registry CORS注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")                  // 匹配所有请求路径
                .allowedOriginPatterns("*")          // 允许所有来源（生产环境建议限制）
                .allowedMethods(                     // 允许的HTTP方法
                        "GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")                 // 允许所有请求头
                .allowCredentials(true)              // 允许携带Cookie
                .maxAge(3600);                       // 预检请求缓存1小时
    }
}
