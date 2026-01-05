package com.bo.chatbot.config;

import com.bo.chatbot.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存配置类
 * 负责缓存服务的初始化和销毁
 */
@Slf4j
@Configuration
public class CacheConfig implements ApplicationRunner {
    
    @Autowired
    private CacheService cacheService;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 应用启动后初始化缓存服务
        cacheService.init();
        log.info("缓存服务已启动");
        
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cacheService.destroy();
            log.info("缓存服务已关闭");
        }));
    }
}