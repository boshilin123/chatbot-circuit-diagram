package com.bo.chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate配置
 */
@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 设置连接超时时间 (30秒)
        factory.setConnectTimeout(30000);
        
        // 设置读取超时时间 (60秒) - DeepSeek API可能需要较长时间处理
        factory.setReadTimeout(60000);
        
        return new RestTemplate(factory);
    }
}