package com.bo.chatbot.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置
 * 注册拦截器和其他Web相关配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册限流拦截器，但排除静态资源和统计接口
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/stats",           // 统计接口
                        "/api/health",          // 健康检查
                        "/api/ratelimit/stats", // 限流统计
                        "/api/cache/stats",     // 缓存统计
                        "/api/monitoring/**"    // 监控接口
                );
    }
}