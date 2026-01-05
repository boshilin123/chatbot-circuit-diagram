package com.bo.chatbot.service;

import com.bo.chatbot.model.QueryInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 优化的查询理解服务
 * 智能判断是否需要AI处理，简单查询直接使用本地关键词提取
 */
@Slf4j
@Service
public class OptimizedQueryUnderstandingService {
    
    @Autowired
    private QueryComplexityAnalyzer complexityAnalyzer;
    
    @Autowired
    private KeywordExtractor keywordExtractor;
    
    @Autowired
    private QueryUnderstandingService aiQueryUnderstandingService;
    
    @Autowired
    private CacheService cacheService;
    
    /**
     * 统计信息
     */
    private final AtomicLong localProcessCount = new AtomicLong(0);
    private final AtomicLong aiProcessCount = new AtomicLong(0);
    private final AtomicLong aiSkipCount = new AtomicLong(0);
    private final AtomicLong aiFailureCount = new AtomicLong(0);
    
    /**
     * 智能查询理解
     * 根据查询复杂度选择处理方式
     */
    public QueryInfo understand(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        
        // 首先检查缓存
        QueryInfo cachedResult = cacheService.getCachedQueryInfo(query);
        if (cachedResult != null) {
            log.debug("使用缓存的查询理解结果: {}", cachedResult);
            return cachedResult;
        }
        
        // 分析查询复杂度
        QueryComplexityAnalyzer.QueryComplexityResult complexity = 
                complexityAnalyzer.analyzeComplexity(query);
        
        QueryInfo result;
        
        if (!complexity.isNeedsAI()) {
            // 简单查询，使用本地处理
            result = processLocally(query, complexity);
            localProcessCount.incrementAndGet();
            log.info("本地处理查询 - Query: '{}', Reason: {}", query, complexity.getReason());
        } else {
            // 复杂查询，尝试AI处理
            try {
                result = processWithAI(query, complexity);
                aiProcessCount.incrementAndGet();
                log.info("AI处理查询 - Query: '{}', Reason: {}", query, complexity.getReason());
            } catch (Exception e) {
                // AI处理失败，降级到本地处理
                log.warn("AI处理失败，降级到本地处理 - Query: '{}', Error: {}", query, e.getMessage());
                result = processLocally(query, complexity);
                aiFailureCount.incrementAndGet();
            }
        }
        
        // 缓存结果
        if (result != null && result.hasValidInfo()) {
            cacheService.cacheQueryInfo(query, result);
        }
        
        return result;
    }
    
    /**
     * 本地处理查询
     */
    private QueryInfo processLocally(String query, QueryComplexityAnalyzer.QueryComplexityResult complexity) {
        QueryInfo queryInfo = new QueryInfo();
        queryInfo.setOriginalQuery(query);
        
        // 提取关键词
        List<String> brands = keywordExtractor.extractBrands(query);
        List<String> ecus = keywordExtractor.extractEcuTypes(query);
        List<String> components = keywordExtractor.extractComponents(query);
        
        // 设置提取的信息
        if (!brands.isEmpty()) {
            queryInfo.setBrand(brands.get(0));
        }
        
        if (!ecus.isEmpty()) {
            queryInfo.setEcuType(ecus.get(0));
        }
        
        if (!components.isEmpty()) {
            queryInfo.setComponent(components.get(0));
        }
        
        // 智能推断型号
        String model = inferModel(query, queryInfo.getBrand());
        if (model != null) {
            queryInfo.setModel(model);
        }
        
        // 推断查询类型
        String queryType = inferQueryType(query, queryInfo);
        queryInfo.setQueryType(queryType);
        
        log.debug("本地处理结果: {}", queryInfo);
        return queryInfo;
    }
    
    /**
     * AI处理查询
     */
    private QueryInfo processWithAI(String query, QueryComplexityAnalyzer.QueryComplexityResult complexity) {
        return aiQueryUnderstandingService.understand(query);
    }
    
    /**
     * 推断型号信息
     */
    private String inferModel(String query, String brand) {
        String lowerQuery = query.toLowerCase();
        
        // 常见型号模式
        if (lowerQuery.contains("天龙")) return "天龙";
        if (lowerQuery.contains("杰狮")) return "杰狮";
        if (lowerQuery.contains("德龙")) return "德龙";
        if (lowerQuery.contains("奥龙")) return "奥龙";
        if (lowerQuery.contains("欧曼")) return "欧曼";
        if (lowerQuery.contains("格尔发")) return "格尔发";
        if (lowerQuery.contains("乘龙")) return "乘龙";
        if (lowerQuery.contains("霸龙")) return "霸龙";
        
        // 工程机械型号
        if (lowerQuery.contains("sy215")) return "SY215";
        if (lowerQuery.contains("sy225")) return "SY225";
        if (lowerQuery.contains("sy235")) return "SY235";
        if (lowerQuery.contains("320d")) return "320D";
        if (lowerQuery.contains("330c")) return "330C";
        if (lowerQuery.contains("336d")) return "336D";
        
        // ECU型号作为model
        if (lowerQuery.contains("4hk1")) return "4HK1";
        if (lowerQuery.contains("6hk1")) return "6HK1";
        if (lowerQuery.contains("c240")) return "C240";
        if (lowerQuery.contains("c280")) return "C280";
        if (lowerQuery.contains("c300")) return "C300";
        if (lowerQuery.contains("c350")) return "C350";
        
        // 数字型号模式
        if (lowerQuery.matches(".*[a-z]*\\d{3,4}[a-z]*.*")) {
            // 提取数字型号，如M500, N5G等
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([a-zA-Z]*\\d{3,4}[a-zA-Z]*)");
            java.util.regex.Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                return matcher.group(1).toUpperCase();
            }
        }
        
        // 如果品牌是东风，且没有找到具体型号，但查询中包含天龙相关词汇
        if ("东风".equals(brand) && lowerQuery.contains("天龙")) {
            return "天龙";
        }
        
        return null;
    }
    
    /**
     * 推断查询类型
     */
    private String inferQueryType(String query, QueryInfo queryInfo) {
        String lowerQuery = query.toLowerCase();
        
        // 根据关键词判断
        if (lowerQuery.contains("ecu") || lowerQuery.contains("电脑板") || 
            lowerQuery.contains("控制器") || queryInfo.getEcuType() != null) {
            return "ECU电路图";
        }
        
        if (lowerQuery.contains("整车") || lowerQuery.contains("全车")) {
            return "整车电路图";
        }
        
        if (lowerQuery.contains("发动机")) {
            return "发动机电路图";
        }
        
        if (lowerQuery.contains("针脚") || lowerQuery.contains("接口")) {
            return "针脚定义图";
        }
        
        // 默认返回整车电路图
        return "整车电路图";
    }
    
    /**
     * 强制使用AI处理（用于特殊情况）
     */
    public QueryInfo forceAIProcessing(String query) {
        try {
            QueryInfo result = aiQueryUnderstandingService.understand(query);
            aiProcessCount.incrementAndGet();
            
            // 缓存结果
            if (result != null && result.hasValidInfo()) {
                cacheService.cacheQueryInfo(query, result);
            }
            
            return result;
        } catch (Exception e) {
            log.error("强制AI处理失败 - Query: '{}'", query, e);
            aiFailureCount.incrementAndGet();
            throw e;
        }
    }
    
    /**
     * 获取处理统计信息
     */
    public ProcessingStats getStats() {
        long total = localProcessCount.get() + aiProcessCount.get();
        double localRate = total > 0 ? (double) localProcessCount.get() / total : 0.0;
        double aiRate = total > 0 ? (double) aiProcessCount.get() / total : 0.0;
        
        return new ProcessingStats(
                localProcessCount.get(),
                aiProcessCount.get(),
                aiSkipCount.get(),
                aiFailureCount.get(),
                localRate,
                aiRate
        );
    }
    
    /**
     * 重置统计信息
     */
    public void resetStats() {
        localProcessCount.set(0);
        aiProcessCount.set(0);
        aiSkipCount.set(0);
        aiFailureCount.set(0);
    }
    
    /**
     * 处理统计信息
     */
    @Data
    public static class ProcessingStats {
        private final long localProcessCount;
        private final long aiProcessCount;
        private final long aiSkipCount;
        private final long aiFailureCount;
        private final double localProcessRate;
        private final double aiProcessRate;
        
        public ProcessingStats(long localProcessCount, long aiProcessCount, 
                             long aiSkipCount, long aiFailureCount,
                             double localProcessRate, double aiProcessRate) {
            this.localProcessCount = localProcessCount;
            this.aiProcessCount = aiProcessCount;
            this.aiSkipCount = aiSkipCount;
            this.aiFailureCount = aiFailureCount;
            this.localProcessRate = localProcessRate;
            this.aiProcessRate = aiProcessRate;
        }
        
        public long getTotalProcessCount() {
            return localProcessCount + aiProcessCount;
        }
        
        public double getAiSuccessRate() {
            long totalAiAttempts = aiProcessCount + aiFailureCount;
            return totalAiAttempts > 0 ? (double) aiProcessCount / totalAiAttempts : 0.0;
        }
    }
}