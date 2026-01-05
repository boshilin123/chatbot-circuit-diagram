package com.bo.chatbot.service;

import com.bo.chatbot.model.CircuitDocument;
import com.bo.chatbot.model.QueryInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存服务
 * 提供搜索结果和AI理解结果的内存缓存功能
 * 使用LRU策略和TTL机制管理缓存
 */
@Slf4j
@Service
public class CacheService {
    
    /**
     * 缓存配置
     */
    private static final int MAX_CACHE_SIZE = 1000;           // 最大缓存条目数
    private static final long SEARCH_CACHE_TTL = 30 * 60 * 1000L;  // 搜索缓存30分钟
    private static final long AI_CACHE_TTL = 2 * 60 * 60 * 1000L;  // AI理解缓存2小时
    private static final long CLEANUP_INTERVAL = 5 * 60 * 1000L;   // 清理间隔5分钟
    
    /**
     * 搜索结果缓存
     */
    private final ConcurrentHashMap<String, CacheEntry<List<CircuitDocument>>> searchCache = new ConcurrentHashMap<>();
    
    /**
     * AI理解结果缓存
     */
    private final ConcurrentHashMap<String, CacheEntry<QueryInfo>> aiCache = new ConcurrentHashMap<>();
    
    /**
     * AI分类结果缓存
     */
    private final ConcurrentHashMap<String, CacheEntry<AIResultCategorizer.AICategoryResult>> aiCategoryCache = new ConcurrentHashMap<>();
    
    /**
     * 缓存统计
     */
    private final AtomicLong searchHitCount = new AtomicLong(0);
    private final AtomicLong searchMissCount = new AtomicLong(0);
    private final AtomicLong aiHitCount = new AtomicLong(0);
    private final AtomicLong aiMissCount = new AtomicLong(0);
    private final AtomicLong aiCategoryHitCount = new AtomicLong(0);
    private final AtomicLong aiCategoryMissCount = new AtomicLong(0);
    
    /**
     * 定时清理任务
     */
    private ScheduledExecutorService cleanupExecutor;
    
    /**
     * 缓存条目
     */
    @Data
    private static class CacheEntry<T> {
        private final T value;
        private final long createTime;
        private final long ttl;
        private volatile long lastAccessTime;
        
        public CacheEntry(T value, long ttl) {
            this.value = value;
            this.createTime = System.currentTimeMillis();
            this.lastAccessTime = this.createTime;
            this.ttl = ttl;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - createTime > ttl;
        }
        
        public void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
    
    /**
     * 初始化缓存服务
     */
    public void init() {
        // 启动定时清理任务
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredEntries, 
                CLEANUP_INTERVAL, CLEANUP_INTERVAL, TimeUnit.MILLISECONDS);
        
        log.info("缓存服务初始化完成 - 最大容量: {}, 搜索TTL: {}分钟, AI TTL: {}分钟", 
                MAX_CACHE_SIZE, SEARCH_CACHE_TTL / 60000, AI_CACHE_TTL / 60000);
    }
    
    /**
     * 销毁缓存服务
     */
    public void destroy() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
        }
        log.info("缓存服务已关闭");
    }
    
    /**
     * 获取缓存的搜索结果
     */
    public List<CircuitDocument> getCachedSearchResult(String query) {
        String cacheKey = normalizeQuery(query);
        CacheEntry<List<CircuitDocument>> entry = searchCache.get(cacheKey);
        
        if (entry != null && !entry.isExpired()) {
            entry.updateAccessTime();
            searchHitCount.incrementAndGet();
            log.debug("搜索缓存命中 - Key: {}", cacheKey);
            return entry.getValue();
        }
        
        // 缓存未命中或已过期
        if (entry != null) {
            searchCache.remove(cacheKey);
        }
        searchMissCount.incrementAndGet();
        log.debug("搜索缓存未命中 - Key: {}", cacheKey);
        return null;
    }
    
    /**
     * 缓存搜索结果
     */
    public void cacheSearchResult(String query, List<CircuitDocument> results) {
        if (results == null || results.isEmpty()) {
            return; // 不缓存空结果
        }
        
        String cacheKey = normalizeQuery(query);
        
        // 检查缓存容量，必要时清理
        if (searchCache.size() >= MAX_CACHE_SIZE) {
            evictLRUEntries(searchCache, MAX_CACHE_SIZE / 4); // 清理25%的条目
        }
        
        CacheEntry<List<CircuitDocument>> entry = new CacheEntry<>(results, SEARCH_CACHE_TTL);
        searchCache.put(cacheKey, entry);
        
        log.debug("搜索结果已缓存 - Key: {}, 结果数: {}", cacheKey, results.size());
    }
    
    /**
     * 获取缓存的AI理解结果
     */
    public QueryInfo getCachedQueryInfo(String query) {
        String cacheKey = query.trim(); // AI理解不做过多标准化，保持原始性
        CacheEntry<QueryInfo> entry = aiCache.get(cacheKey);
        
        if (entry != null && !entry.isExpired()) {
            entry.updateAccessTime();
            aiHitCount.incrementAndGet();
            log.debug("AI理解缓存命中 - Key: {}", cacheKey);
            return entry.getValue();
        }
        
        // 缓存未命中或已过期
        if (entry != null) {
            aiCache.remove(cacheKey);
        }
        aiMissCount.incrementAndGet();
        log.debug("AI理解缓存未命中 - Key: {}", cacheKey);
        return null;
    }
    
    /**
     * 缓存AI理解结果
     */
    public void cacheQueryInfo(String query, QueryInfo queryInfo) {
        if (queryInfo == null || !queryInfo.hasValidInfo()) {
            return; // 不缓存无效结果
        }
        
        String cacheKey = query.trim();
        
        // 检查缓存容量
        if (aiCache.size() >= MAX_CACHE_SIZE) {
            evictLRUEntries(aiCache, MAX_CACHE_SIZE / 4);
        }
        
        CacheEntry<QueryInfo> entry = new CacheEntry<>(queryInfo, AI_CACHE_TTL);
        aiCache.put(cacheKey, entry);
        
        log.debug("AI理解结果已缓存 - Key: {}", cacheKey);
    }
    
    /**
     * 获取缓存的AI分类结果
     */
    public AIResultCategorizer.AICategoryResult getCachedAICategoryResult(String query) {
        String cacheKey = query.trim();
        CacheEntry<AIResultCategorizer.AICategoryResult> entry = aiCategoryCache.get(cacheKey);
        
        if (entry != null && !entry.isExpired()) {
            entry.updateAccessTime();
            aiCategoryHitCount.incrementAndGet();
            log.debug("AI分类缓存命中 - Key: {}", cacheKey);
            return entry.getValue();
        }
        
        // 缓存未命中或已过期
        if (entry != null) {
            aiCategoryCache.remove(cacheKey);
        }
        aiCategoryMissCount.incrementAndGet();
        log.debug("AI分类缓存未命中 - Key: {}", cacheKey);
        return null;
    }
    
    /**
     * 缓存AI分类结果
     */
    public void cacheAICategoryResult(String query, AIResultCategorizer.AICategoryResult result) {
        if (result == null || result.getOptions() == null || result.getOptions().isEmpty()) {
            return; // 不缓存无效结果
        }
        
        String cacheKey = query.trim();
        
        // 检查缓存容量
        if (aiCategoryCache.size() >= MAX_CACHE_SIZE) {
            evictLRUEntries(aiCategoryCache, MAX_CACHE_SIZE / 4);
        }
        
        CacheEntry<AIResultCategorizer.AICategoryResult> entry = new CacheEntry<>(result, AI_CACHE_TTL);
        aiCategoryCache.put(cacheKey, entry);
        
        log.debug("AI分类结果已缓存 - Key: {}, 分类数: {}", cacheKey, result.getOptions().size());
    }
    
    /**
     * 查询标准化
     * 提高缓存命中率
     */
    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        
        return query.trim()
                .toLowerCase()
                .replaceAll("\\s+", " ")           // 多空格合并为单空格
                .replace("电路图", "")              // 去掉通用词
                .replace("图纸", "")
                .replace("资料", "")
                .replace("ecu", "ECU")             // 统一ECU大小写
                .replace("保险丝盒", "保险丝")       // 同义词替换
                .replace("接线盒", "保险丝")
                .replace("针脚图", "针脚定义")
                .replace("接口图", "针脚定义")
                .trim();
    }
    
    /**
     * LRU淘汰策略
     * 根据最后访问时间淘汰最久未使用的条目
     */
    private <T> void evictLRUEntries(ConcurrentHashMap<String, CacheEntry<T>> cache, int evictCount) {
        cache.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e1.getValue().getLastAccessTime(), 
                                                e2.getValue().getLastAccessTime()))
                .limit(evictCount)
                .forEach(entry -> {
                    cache.remove(entry.getKey());
                    log.debug("LRU淘汰缓存条目 - Key: {}", entry.getKey());
                });
    }
    
    /**
     * 清理过期条目
     */
    private void cleanupExpiredEntries() {
        try {
            int searchExpired = 0;
            int aiExpired = 0;
            int aiCategoryExpired = 0;
            
            // 清理过期的搜索缓存
            for (var entry : searchCache.entrySet()) {
                if (entry.getValue().isExpired()) {
                    searchCache.remove(entry.getKey());
                    searchExpired++;
                }
            }
            
            // 清理过期的AI缓存
            for (var entry : aiCache.entrySet()) {
                if (entry.getValue().isExpired()) {
                    aiCache.remove(entry.getKey());
                    aiExpired++;
                }
            }
            
            // 清理过期的AI分类缓存
            for (var entry : aiCategoryCache.entrySet()) {
                if (entry.getValue().isExpired()) {
                    aiCategoryCache.remove(entry.getKey());
                    aiCategoryExpired++;
                }
            }
            
            if (searchExpired > 0 || aiExpired > 0 || aiCategoryExpired > 0) {
                log.debug("清理过期缓存 - 搜索: {}, AI: {}, AI分类: {}", searchExpired, aiExpired, aiCategoryExpired);
            }
            
        } catch (Exception e) {
            log.error("清理过期缓存失败", e);
        }
    }
    
    /**
     * 清空所有缓存
     */
    public void clearAllCache() {
        searchCache.clear();
        aiCache.clear();
        aiCategoryCache.clear();
        
        // 重置统计
        searchHitCount.set(0);
        searchMissCount.set(0);
        aiHitCount.set(0);
        aiMissCount.set(0);
        aiCategoryHitCount.set(0);
        aiCategoryMissCount.set(0);
        
        log.info("所有缓存已清空");
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        return new CacheStats(
                searchCache.size(),
                aiCache.size(),
                aiCategoryCache.size(),
                searchHitCount.get(),
                searchMissCount.get(),
                aiHitCount.get(),
                aiMissCount.get(),
                aiCategoryHitCount.get(),
                aiCategoryMissCount.get()
        );
    }
    
    /**
     * 缓存统计信息
     */
    @Data
    public static class CacheStats {
        private final int searchCacheSize;
        private final int aiCacheSize;
        private final int aiCategoryCacheSize;
        private final long searchHitCount;
        private final long searchMissCount;
        private final long aiHitCount;
        private final long aiMissCount;
        private final long aiCategoryHitCount;
        private final long aiCategoryMissCount;
        
        public double getSearchHitRate() {
            long total = searchHitCount + searchMissCount;
            return total > 0 ? (double) searchHitCount / total : 0.0;
        }
        
        public double getAiHitRate() {
            long total = aiHitCount + aiMissCount;
            return total > 0 ? (double) aiHitCount / total : 0.0;
        }
        
        public double getAiCategoryHitRate() {
            long total = aiCategoryHitCount + aiCategoryMissCount;
            return total > 0 ? (double) aiCategoryHitCount / total : 0.0;
        }
        
        public int getTotalCacheSize() {
            return searchCacheSize + aiCacheSize + aiCategoryCacheSize;
        }
    }
}