package com.bo.chatbot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * AI配置验证
 */
@Slf4j
@Component
public class AIConfig {
    
    @Value("${deepseek.api.key:}")
    private String apiKey;
    
    @Value("${deepseek.api.url:}")
    private String apiUrl;
    
    /**
     * 应用启动后验证配置
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        log.info("========== AI配置检查 ==========");
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("⚠️  DeepSeek API Key未配置！AI分类功能将不可用");
            log.warn("   请在application.yml中配置 deepseek.api.key");
        } else {
            log.info("✅ DeepSeek API Key已配置 (长度: {})", apiKey.length());
        }
        
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            log.warn("⚠️  DeepSeek API URL未配置！");
        } else {
            log.info("✅ DeepSeek API URL: {}", apiUrl);
        }
        
        log.info("===============================");
    }
    
    /**
     * 检查AI功能是否可用
     */
    public boolean isAIEnabled() {
        return apiKey != null && !apiKey.trim().isEmpty() && 
               apiUrl != null && !apiUrl.trim().isEmpty();
    }
}