package com.bo.chatbot.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 监控服务
 * 提供系统性能监控、异常监控和日志记录功能
 */
@Slf4j
@Service
public class MonitoringService {
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @Autowired
    private OptimizedQueryUnderstandingService optimizedQueryUnderstandingService;
    
    /**
     * 性能指标
     */
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong slowQueryCount = new AtomicLong(0);
    
    /**
     * 慢查询阈值（毫秒）
     */
    private static final long SLOW_QUERY_THRESHOLD = 3000;
    
    /**
     * 异常统计
     */
    private final ConcurrentHashMap<String, AtomicLong> exceptionStats = new ConcurrentHashMap<>();
    
    /**
     * 性能历史记录（最近24小时）
     */
    private final ConcurrentHashMap<String, PerformanceRecord> hourlyStats = new ConcurrentHashMap<>();
    
    /**
     * 定时任务执行器
     */
    private final ScheduledExecutorService scheduler;
    
    /**
     * 日志格式化器
     */
    private final DateTimeFormatter logFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public MonitoringService() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "monitoring-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 每小时清理过期的性能记录
        scheduler.scheduleAtFixedRate(this::cleanupOldRecords, 1, 1, TimeUnit.HOURS);
        
        // 每5分钟输出性能摘要
        scheduler.scheduleAtFixedRate(this::logPerformanceSummary, 5, 5, TimeUnit.MINUTES);
        
        log.info("监控服务初始化完成 - 慢查询阈值: {}ms", SLOW_QUERY_THRESHOLD);
    }
    
    /**
     * 记录请求开始
     */
    public RequestContext startRequest(String sessionId, String message, String clientIp) {
        RequestContext context = new RequestContext(sessionId, message, clientIp);
        
        // 结构化日志记录
        logStructured("REQUEST_START", 
                "sessionId", sessionId,
                "clientIp", clientIp,
                "messageLength", message.length(),
                "timestamp", LocalDateTime.now().format(logFormatter));
        
        return context;
    }
    
    /**
     * 记录请求完成
     */
    public void endRequest(RequestContext context, boolean success, String errorMessage) {
        long responseTime = System.currentTimeMillis() - context.getStartTime();
        
        // 更新统计
        totalRequests.incrementAndGet();
        totalResponseTime.addAndGet(responseTime);
        
        if (!success) {
            errorCount.incrementAndGet();
        }
        
        if (responseTime > SLOW_QUERY_THRESHOLD) {
            slowQueryCount.incrementAndGet();
            logStructured("SLOW_QUERY", 
                    "sessionId", context.getSessionId(),
                    "responseTime", responseTime,
                    "message", context.getMessage(),
                    "threshold", SLOW_QUERY_THRESHOLD);
        }
        
        // 记录到小时统计
        String hourKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        PerformanceRecord record = hourlyStats.computeIfAbsent(hourKey, k -> new PerformanceRecord());
        record.addRequest(responseTime, success);
        
        // 结构化日志记录
        logStructured("REQUEST_END", 
                "sessionId", context.getSessionId(),
                "responseTime", responseTime,
                "success", success,
                "errorMessage", errorMessage,
                "timestamp", LocalDateTime.now().format(logFormatter));
    }
    
    /**
     * 记录异常
     */
    public void recordException(String exceptionType, String message, Throwable throwable) {
        exceptionStats.computeIfAbsent(exceptionType, k -> new AtomicLong(0)).incrementAndGet();
        
        logStructured("EXCEPTION", 
                "type", exceptionType,
                "message", message,
                "stackTrace", throwable != null ? throwable.getClass().getSimpleName() : "null",
                "timestamp", LocalDateTime.now().format(logFormatter));
    }
    
    /**
     * 记录缓存命中/未命中
     */
    public void recordCacheEvent(String cacheType, boolean hit, String key) {
        logStructured("CACHE_EVENT", 
                "type", cacheType,
                "hit", hit,
                "keyHash", key.hashCode(),
                "timestamp", LocalDateTime.now().format(logFormatter));
    }
    
    /**
     * 记录AI处理事件
     */
    public void recordAIEvent(String eventType, String query, long processingTime, boolean success) {
        logStructured("AI_EVENT", 
                "eventType", eventType,
                "queryLength", query.length(),
                "processingTime", processingTime,
                "success", success,
                "timestamp", LocalDateTime.now().format(logFormatter));
    }
    
    /**
     * 记录搜索事件
     */
    public void recordSearchEvent(String searchType, int resultCount, long searchTime) {
        logStructured("SEARCH_EVENT", 
                "searchType", searchType,
                "resultCount", resultCount,
                "searchTime", searchTime,
                "timestamp", LocalDateTime.now().format(logFormatter));
    }
    
    /**
     * 获取系统健康状态
     */
    public SystemHealth getSystemHealth() {
        long totalReqs = totalRequests.get();
        double avgResponseTime = totalReqs > 0 ? (double) totalResponseTime.get() / totalReqs : 0.0;
        double errorRate = totalReqs > 0 ? (double) errorCount.get() / totalReqs : 0.0;
        double slowQueryRate = totalReqs > 0 ? (double) slowQueryCount.get() / totalReqs : 0.0;
        
        // 获取各服务状态
        CacheService.CacheStats cacheStats = cacheService.getStats();
        RateLimitService.RateLimitStats rateLimitStats = rateLimitService.getStats();
        OptimizedQueryUnderstandingService.ProcessingStats processingStats = 
                optimizedQueryUnderstandingService.getStats();
        
        // 判断健康状态
        HealthStatus status = determineHealthStatus(errorRate, slowQueryRate, avgResponseTime);
        
        return new SystemHealth(
                status,
                avgResponseTime,
                errorRate,
                slowQueryRate,
                totalReqs,
                errorCount.get(),
                slowQueryCount.get(),
                cacheStats,
                rateLimitStats,
                processingStats,
                exceptionStats,
                LocalDateTime.now()
        );
    }
    
    /**
     * 获取性能趋势数据
     */
    public java.util.Map<String, PerformanceRecord> getPerformanceTrend() {
        return new java.util.HashMap<>(hourlyStats);
    }
    
    /**
     * 重置监控统计
     */
    public void resetStats() {
        totalResponseTime.set(0);
        totalRequests.set(0);
        errorCount.set(0);
        slowQueryCount.set(0);
        exceptionStats.clear();
        hourlyStats.clear();
        
        log.info("监控统计已重置");
    }
    
    /**
     * 结构化日志记录
     */
    private void logStructured(String eventType, Object... keyValues) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(eventType).append("] ");
        
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) sb.append(", ");
            sb.append(keyValues[i]).append("=").append(keyValues[i + 1]);
        }
        
        log.info(sb.toString());
    }
    
    /**
     * 定期输出性能摘要
     */
    private void logPerformanceSummary() {
        try {
            SystemHealth health = getSystemHealth();
            
            log.info("=== 性能摘要 ===");
            log.info("系统状态: {}", health.getStatus());
            log.info("总请求数: {}, 错误数: {}, 慢查询数: {}", 
                    health.getTotalRequests(), health.getErrorCount(), health.getSlowQueryCount());
            log.info("平均响应时间: {:.2f}ms, 错误率: {:.2f}%, 慢查询率: {:.2f}%", 
                    health.getAvgResponseTime(), health.getErrorRate() * 100, health.getSlowQueryRate() * 100);
            log.info("缓存命中率: 搜索={:.2f}%, AI={:.2f}%", 
                    health.getCacheStats().getSearchHitRate() * 100, 
                    health.getCacheStats().getAiHitRate() * 100);
            log.info("查询处理: 本地={}, AI={}, 失败={}", 
                    health.getProcessingStats().getLocalProcessCount(),
                    health.getProcessingStats().getAiProcessCount(),
                    health.getProcessingStats().getAiFailureCount());
            log.info("限流状态: 总请求={}, 被阻止={}, 当前并发={}", 
                    health.getRateLimitStats().getTotalRequests(),
                    health.getRateLimitStats().getBlockedRequests(),
                    health.getRateLimitStats().getCurrentConcurrentRequests());
            
        } catch (Exception e) {
            log.error("输出性能摘要失败", e);
        }
    }
    
    /**
     * 清理过期记录
     */
    private void cleanupOldRecords() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            String cutoffKey = cutoff.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
            
            hourlyStats.entrySet().removeIf(entry -> entry.getKey().compareTo(cutoffKey) < 0);
            
            log.debug("清理过期性能记录，保留最近24小时数据");
        } catch (Exception e) {
            log.error("清理过期记录失败", e);
        }
    }
    
    /**
     * 判断系统健康状态
     */
    private HealthStatus determineHealthStatus(double errorRate, double slowQueryRate, double avgResponseTime) {
        if (errorRate > 0.1 || slowQueryRate > 0.3 || avgResponseTime > 5000) {
            return HealthStatus.CRITICAL;
        } else if (errorRate > 0.05 || slowQueryRate > 0.15 || avgResponseTime > 3000) {
            return HealthStatus.WARNING;
        } else {
            return HealthStatus.HEALTHY;
        }
    }
    
    /**
     * 请求上下文
     */
    @Data
    public static class RequestContext {
        private final String sessionId;
        private final String message;
        private final String clientIp;
        private final long startTime;
        
        public RequestContext(String sessionId, String message, String clientIp) {
            this.sessionId = sessionId;
            this.message = message;
            this.clientIp = clientIp;
            this.startTime = System.currentTimeMillis();
        }
    }
    
    /**
     * 性能记录
     */
    @Data
    public static class PerformanceRecord {
        private long totalRequests = 0;
        private long totalResponseTime = 0;
        private long errorCount = 0;
        private long slowQueryCount = 0;
        
        public synchronized void addRequest(long responseTime, boolean success) {
            totalRequests++;
            totalResponseTime += responseTime;
            if (!success) {
                errorCount++;
            }
            if (responseTime > SLOW_QUERY_THRESHOLD) {
                slowQueryCount++;
            }
        }
        
        public double getAvgResponseTime() {
            return totalRequests > 0 ? (double) totalResponseTime / totalRequests : 0.0;
        }
        
        public double getErrorRate() {
            return totalRequests > 0 ? (double) errorCount / totalRequests : 0.0;
        }
        
        public double getSlowQueryRate() {
            return totalRequests > 0 ? (double) slowQueryCount / totalRequests : 0.0;
        }
    }
    
    /**
     * 系统健康状态
     */
    @Data
    public static class SystemHealth {
        private final HealthStatus status;
        private final double avgResponseTime;
        private final double errorRate;
        private final double slowQueryRate;
        private final long totalRequests;
        private final long errorCount;
        private final long slowQueryCount;
        private final CacheService.CacheStats cacheStats;
        private final RateLimitService.RateLimitStats rateLimitStats;
        private final OptimizedQueryUnderstandingService.ProcessingStats processingStats;
        private final java.util.Map<String, AtomicLong> exceptionStats;
        private final LocalDateTime timestamp;
    }
    
    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        HEALTHY("健康"),
        WARNING("警告"),
        CRITICAL("严重");
        
        private final String description;
        
        HealthStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}