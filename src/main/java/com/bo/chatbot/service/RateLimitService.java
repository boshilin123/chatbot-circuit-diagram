package com.bo.chatbot.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求限流服务
 * 实现基于IP和会话的请求限流，防止系统过载
 */
@Slf4j
@Service
public class RateLimitService {
    
    /**
     * 限流配置
     */
    private static final int MAX_REQUESTS_PER_MINUTE_PER_IP = 30;      // 每IP每分钟最大请求数
    private static final int MAX_REQUESTS_PER_MINUTE_PER_SESSION = 20; // 每会话每分钟最大请求数
    private static final int MAX_CONCURRENT_REQUESTS = 50;             // 最大并发请求数
    private static final int AI_REQUESTS_PER_MINUTE_LIMIT = 10;        // AI请求每分钟限制
    private static final long CLEANUP_INTERVAL = 2 * 60 * 1000L;      // 清理间隔2分钟
    
    /**
     * IP限流记录
     */
    private final ConcurrentHashMap<String, RateLimitRecord> ipLimitMap = new ConcurrentHashMap<>();
    
    /**
     * 会话限流记录
     */
    private final ConcurrentHashMap<String, RateLimitRecord> sessionLimitMap = new ConcurrentHashMap<>();
    
    /**
     * AI请求限流记录
     */
    private final ConcurrentHashMap<String, RateLimitRecord> aiRequestLimitMap = new ConcurrentHashMap<>();
    
    /**
     * 当前并发请求数
     */
    private final AtomicInteger currentConcurrentRequests = new AtomicInteger(0);
    
    /**
     * 统计信息
     */
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong blockedRequests = new AtomicLong(0);
    private final AtomicLong aiRequestsBlocked = new AtomicLong(0);
    
    /**
     * 定时清理任务
     */
    private final ScheduledExecutorService cleanupExecutor;
    
    public RateLimitService() {
        // 启动定时清理任务
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        });
        
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredRecords, 
                CLEANUP_INTERVAL, CLEANUP_INTERVAL, TimeUnit.MILLISECONDS);
        
        log.info("限流服务初始化完成 - IP限制: {}/分钟, 会话限制: {}/分钟, 最大并发: {}", 
                MAX_REQUESTS_PER_MINUTE_PER_IP, MAX_REQUESTS_PER_MINUTE_PER_SESSION, MAX_CONCURRENT_REQUESTS);
    }
    
    /**
     * 检查请求是否被限流
     */
    public RateLimitResult checkRateLimit(String clientIp, String sessionId) {
        totalRequests.incrementAndGet();
        
        // 1. 检查并发请求数
        int concurrent = currentConcurrentRequests.get();
        if (concurrent >= MAX_CONCURRENT_REQUESTS) {
            blockedRequests.incrementAndGet();
            log.warn("并发请求数超限 - 当前: {}, 限制: {}", concurrent, MAX_CONCURRENT_REQUESTS);
            return new RateLimitResult(false, "系统繁忙，请稍后重试", "CONCURRENT_LIMIT");
        }
        
        // 2. 检查IP限流
        if (!checkIpRateLimit(clientIp)) {
            blockedRequests.incrementAndGet();
            log.warn("IP请求频率超限 - IP: {}", clientIp);
            return new RateLimitResult(false, "请求过于频繁，请稍后重试", "IP_RATE_LIMIT");
        }
        
        // 3. 检查会话限流
        if (!checkSessionRateLimit(sessionId)) {
            blockedRequests.incrementAndGet();
            log.warn("会话请求频率超限 - Session: {}", sessionId);
            return new RateLimitResult(false, "请求过于频繁，请稍后重试", "SESSION_RATE_LIMIT");
        }
        
        // 通过限流检查，增加并发计数
        currentConcurrentRequests.incrementAndGet();
        
        return new RateLimitResult(true, "通过", "ALLOWED");
    }
    
    /**
     * 检查AI请求限流
     */
    public boolean checkAiRequestLimit(String clientIp) {
        String key = "ai_" + clientIp;
        RateLimitRecord record = aiRequestLimitMap.computeIfAbsent(key, k -> new RateLimitRecord());
        
        LocalDateTime now = LocalDateTime.now();
        
        // 清理过期的请求记录
        record.getRequestTimes().removeIf(time -> 
                ChronoUnit.MINUTES.between(time, now) >= 1);
        
        if (record.getRequestTimes().size() >= AI_REQUESTS_PER_MINUTE_LIMIT) {
            aiRequestsBlocked.incrementAndGet();
            log.warn("AI请求频率超限 - IP: {}, 当前: {}/分钟", clientIp, record.getRequestTimes().size());
            return false;
        }
        
        record.getRequestTimes().add(now);
        record.setLastRequestTime(now);
        
        return true;
    }
    
    /**
     * 请求完成，减少并发计数
     */
    public void requestCompleted() {
        currentConcurrentRequests.decrementAndGet();
    }
    
    /**
     * 检查IP限流
     */
    private boolean checkIpRateLimit(String clientIp) {
        RateLimitRecord record = ipLimitMap.computeIfAbsent(clientIp, k -> new RateLimitRecord());
        return checkRateLimit(record, MAX_REQUESTS_PER_MINUTE_PER_IP);
    }
    
    /**
     * 检查会话限流
     */
    private boolean checkSessionRateLimit(String sessionId) {
        RateLimitRecord record = sessionLimitMap.computeIfAbsent(sessionId, k -> new RateLimitRecord());
        return checkRateLimit(record, MAX_REQUESTS_PER_MINUTE_PER_SESSION);
    }
    
    /**
     * 通用限流检查逻辑
     */
    private boolean checkRateLimit(RateLimitRecord record, int maxRequests) {
        LocalDateTime now = LocalDateTime.now();
        
        // 清理1分钟前的请求记录
        record.getRequestTimes().removeIf(time -> 
                ChronoUnit.MINUTES.between(time, now) >= 1);
        
        // 检查是否超过限制
        if (record.getRequestTimes().size() >= maxRequests) {
            return false;
        }
        
        // 记录本次请求
        record.getRequestTimes().add(now);
        record.setLastRequestTime(now);
        
        return true;
    }
    
    /**
     * 清理过期记录
     */
    private void cleanupExpiredRecords() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minus(5, ChronoUnit.MINUTES);
            
            int ipCleaned = cleanupMap(ipLimitMap, cutoff);
            int sessionCleaned = cleanupMap(sessionLimitMap, cutoff);
            int aiCleaned = cleanupMap(aiRequestLimitMap, cutoff);
            
            if (ipCleaned > 0 || sessionCleaned > 0 || aiCleaned > 0) {
                log.debug("清理过期限流记录 - IP: {}, Session: {}, AI: {}", 
                        ipCleaned, sessionCleaned, aiCleaned);
            }
            
        } catch (Exception e) {
            log.error("清理过期限流记录失败", e);
        }
    }
    
    /**
     * 清理指定Map中的过期记录
     */
    private int cleanupMap(ConcurrentHashMap<String, RateLimitRecord> map, LocalDateTime cutoff) {
        int cleaned = 0;
        for (var entry : map.entrySet()) {
            RateLimitRecord record = entry.getValue();
            if (record.getLastRequestTime().isBefore(cutoff)) {
                map.remove(entry.getKey());
                cleaned++;
            }
        }
        return cleaned;
    }
    
    /**
     * 获取限流统计信息
     */
    public RateLimitStats getStats() {
        return new RateLimitStats(
                totalRequests.get(),
                blockedRequests.get(),
                aiRequestsBlocked.get(),
                currentConcurrentRequests.get(),
                ipLimitMap.size(),
                sessionLimitMap.size(),
                aiRequestLimitMap.size()
        );
    }
    
    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalRequests.set(0);
        blockedRequests.set(0);
        aiRequestsBlocked.set(0);
    }
    
    /**
     * 限流记录
     */
    @Data
    private static class RateLimitRecord {
        private final java.util.List<LocalDateTime> requestTimes = new java.util.ArrayList<>();
        private LocalDateTime lastRequestTime = LocalDateTime.now();
    }
    
    /**
     * 限流检查结果
     */
    @Data
    public static class RateLimitResult {
        private final boolean allowed;
        private final String message;
        private final String reason;
        
        public RateLimitResult(boolean allowed, String message, String reason) {
            this.allowed = allowed;
            this.message = message;
            this.reason = reason;
        }
    }
    
    /**
     * 限流统计信息
     */
    @Data
    public static class RateLimitStats {
        private final long totalRequests;
        private final long blockedRequests;
        private final long aiRequestsBlocked;
        private final int currentConcurrentRequests;
        private final int activeIpRecords;
        private final int activeSessionRecords;
        private final int activeAiRecords;
        
        public RateLimitStats(long totalRequests, long blockedRequests, long aiRequestsBlocked,
                             int currentConcurrentRequests, int activeIpRecords, 
                             int activeSessionRecords, int activeAiRecords) {
            this.totalRequests = totalRequests;
            this.blockedRequests = blockedRequests;
            this.aiRequestsBlocked = aiRequestsBlocked;
            this.currentConcurrentRequests = currentConcurrentRequests;
            this.activeIpRecords = activeIpRecords;
            this.activeSessionRecords = activeSessionRecords;
            this.activeAiRecords = activeAiRecords;
        }
        
        public double getBlockedRate() {
            return totalRequests > 0 ? (double) blockedRequests / totalRequests : 0.0;
        }
        
        public double getAiBlockedRate() {
            long totalAiRequests = aiRequestsBlocked + activeAiRecords;
            return totalAiRequests > 0 ? (double) aiRequestsBlocked / totalAiRequests : 0.0;
        }
    }
}