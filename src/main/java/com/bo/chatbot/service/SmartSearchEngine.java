package com.bo.chatbot.service;

import com.bo.chatbot.model.CircuitDocument;
import com.bo.chatbot.model.QueryInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能搜索引擎
 * 整合多维度索引、相似度评分，提供智能搜索功能
 * 
 * 搜索策略：
 * 1. 优先使用交集搜索（精确匹配）
 * 2. 交集为空时降级到并集搜索（宽松匹配）
 * 3. 对结果进行相似度评分和排序
 * 4. 返回 Top-K 结果
 */
@Slf4j
@Service
public class SmartSearchEngine {
    
    /**
     * 默认返回结果数量
     * 扩大到100条，为AI分类提供更大的范围
     */
    private static final int DEFAULT_TOP_K = 100;
    
    /**
     * 最小相似度阈值（低于此分数的结果会被过滤）
     */
    private static final double MIN_SCORE_THRESHOLD = 0.1;
    
    @Autowired
    private SearchIndex searchIndex;
    
    @Autowired
    private SimilarityScorer similarityScorer;
    
    @Autowired
    private KeywordExtractor keywordExtractor;
    
    /**
     * 智能搜索主方法
     * 
     * @param queryInfo AI 理解后的查询信息
     * @return 排序后的搜索结果
     */
    public List<CircuitDocument> search(QueryInfo queryInfo) {
        return search(queryInfo, DEFAULT_TOP_K);
    }
    
    /**
     * 智能搜索（指定返回数量）
     * 
     * @param queryInfo AI 理解后的查询信息
     * @param topK 返回结果数量
     * @return 排序后的搜索结果
     */
    public List<CircuitDocument> search(QueryInfo queryInfo, int topK) {
        if (queryInfo == null || !queryInfo.hasValidInfo()) {
            log.warn("QueryInfo 为空或无有效信息");
            return Collections.emptyList();
        }
        
        long startTime = System.currentTimeMillis();
        
        // 第一步：使用交集搜索获取候选文档
        Set<Integer> candidates = searchIndex.searchIntersection(queryInfo);
        log.debug("交集搜索结果数: {}", candidates.size());
        
        // 如果交集为空，降级到并集搜索
        if (candidates.isEmpty()) {
            candidates = searchIndex.searchUnion(queryInfo);
            log.debug("降级到并集搜索，结果数: {}", candidates.size());
        }
        
        // 如果仍然为空，尝试全文搜索
        if (candidates.isEmpty()) {
            candidates = fallbackSearch(queryInfo);
            log.debug("降级到全文搜索，结果数: {}", candidates.size());
        }
        
        // 第二步：获取文档对象
        List<CircuitDocument> documents = searchIndex.getDocuments(candidates);
        
        // 第三步：计算相似度分数并排序
        List<ScoredDocument> scoredDocs = documents.stream()
                .map(doc -> new ScoredDocument(doc, similarityScorer.calculateScore(doc, queryInfo)))
                .filter(sd -> sd.getScore() >= MIN_SCORE_THRESHOLD)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("智能搜索完成 - QueryInfo: {}, 候选数: {}, 返回数: {}, 耗时: {}ms",
                queryInfo, candidates.size(), scoredDocs.size(), duration);
        
        // 输出前几个结果的分数（调试用）
        if (log.isDebugEnabled() && !scoredDocs.isEmpty()) {
            scoredDocs.stream().limit(3).forEach(sd -> 
                log.debug("  - [{}] {} (分数: {})", sd.getDoc().getId(), 
                        sd.getDoc().getFileName(), String.format("%.1f", sd.getScore())));
        }
        
        return scoredDocs.stream()
                .map(ScoredDocument::getDoc)
                .collect(Collectors.toList());
    }

    /**
     * 降级搜索：当索引搜索无结果时，使用原始关键词搜索
     */
    private Set<Integer> fallbackSearch(QueryInfo queryInfo) {
        Set<Integer> results = new HashSet<>();
        
        // 使用原始查询进行全文搜索
        if (queryInfo.getOriginalQuery() != null) {
            results.addAll(searchIndex.searchFullText(queryInfo.getOriginalQuery()));
        }
        
        // 使用各字段进行全文搜索
        if (queryInfo.getBrand() != null) {
            results.addAll(searchIndex.searchFullText(queryInfo.getBrand()));
        }
        if (queryInfo.getModel() != null) {
            results.addAll(searchIndex.searchFullText(queryInfo.getModel()));
        }
        if (queryInfo.getComponent() != null) {
            results.addAll(searchIndex.searchFullText(queryInfo.getComponent()));
        }
        if (queryInfo.getEcuType() != null) {
            results.addAll(searchIndex.searchFullText(queryInfo.getEcuType()));
        }
        
        return results;
    }
    
    /**
     * 使用原始关键词搜索（兼容旧接口）
     * 
     * @param keyword 搜索关键词
     * @return 搜索结果
     */
    public List<CircuitDocument> searchByKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        // 构建简单的 QueryInfo
        QueryInfo queryInfo = new QueryInfo();
        queryInfo.setOriginalQuery(keyword);
        
        // 尝试从关键词中提取信息
        List<String> brands = keywordExtractor.extractBrands(keyword);
        if (!brands.isEmpty()) {
            queryInfo.setBrand(brands.get(0));
        }
        
        List<String> ecus = keywordExtractor.extractEcuTypes(keyword);
        if (!ecus.isEmpty()) {
            queryInfo.setEcuType(ecus.get(0));
        }
        
        List<String> components = keywordExtractor.extractComponents(keyword);
        if (!components.isEmpty()) {
            queryInfo.setComponent(components.get(0));
        }
        
        // 如果没有提取到任何信息，使用全文搜索
        if (!queryInfo.hasValidInfo()) {
            Set<Integer> ids = searchIndex.searchFullText(keyword);
            return searchIndex.getDocuments(ids);
        }
        
        return search(queryInfo);
    }
    
    /**
     * 获取搜索结果及其分数（用于调试和分析）
     */
    public List<ScoredDocument> searchWithScores(QueryInfo queryInfo, int topK) {
        if (queryInfo == null || !queryInfo.hasValidInfo()) {
            return Collections.emptyList();
        }
        
        Set<Integer> candidates = searchIndex.searchIntersection(queryInfo);
        if (candidates.isEmpty()) {
            candidates = searchIndex.searchUnion(queryInfo);
        }
        if (candidates.isEmpty()) {
            candidates = fallbackSearch(queryInfo);
        }
        
        List<CircuitDocument> documents = searchIndex.getDocuments(candidates);
        
        return documents.stream()
                .map(doc -> new ScoredDocument(doc, similarityScorer.calculateScore(doc, queryInfo)))
                .filter(sd -> sd.getScore() >= MIN_SCORE_THRESHOLD)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取搜索统计信息
     */
    public Map<String, Object> getSearchStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.putAll(searchIndex.getIndexStats());
        stats.put("minScoreThreshold", MIN_SCORE_THRESHOLD);
        stats.put("defaultTopK", DEFAULT_TOP_K);
        return stats;
    }
    
    /**
     * 带分数的文档包装类
     */
    public static class ScoredDocument {
        private final CircuitDocument doc;
        private final double score;
        
        public ScoredDocument(CircuitDocument doc, double score) {
            this.doc = doc;
            this.score = score;
        }
        
        public CircuitDocument getDoc() {
            return doc;
        }
        
        public double getScore() {
            return score;
        }
        
        @Override
        public String toString() {
            return String.format("ScoredDocument{id=%d, score=%.1f, file='%s'}",
                    doc.getId(), score, doc.getFileName());
        }
    }
}
