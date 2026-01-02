package com.bo.chatbot.service;

import com.bo.chatbot.model.CircuitDocument;
import com.bo.chatbot.model.QueryInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 多维度搜索索引
 * 在应用启动时构建索引，支持按品牌、型号、ECU、部件等维度快速检索
 */
@Slf4j
@Service
public class SearchIndex {
    
    @Autowired
    private DataLoaderService dataLoaderService;
    
    @Autowired
    private KeywordExtractor keywordExtractor;
    
    /**
     * 品牌索引：品牌名 -> 文档ID列表
     */
    private Map<String, Set<Integer>> brandIndex = new ConcurrentHashMap<>();
    
    /**
     * 型号索引：型号名 -> 文档ID列表
     */
    private Map<String, Set<Integer>> modelIndex = new ConcurrentHashMap<>();
    
    /**
     * ECU 索引：ECU类型 -> 文档ID列表
     */
    private Map<String, Set<Integer>> ecuIndex = new ConcurrentHashMap<>();
    
    /**
     * 部件索引：部件类型 -> 文档ID列表
     */
    private Map<String, Set<Integer>> componentIndex = new ConcurrentHashMap<>();
    
    /**
     * 全文索引：关键词 -> 文档ID列表
     */
    private Map<String, Set<Integer>> fullTextIndex = new ConcurrentHashMap<>();
    
    /**
     * 文档ID -> 文档对象的映射
     */
    private Map<Integer, CircuitDocument> documentMap = new ConcurrentHashMap<>();
    
    /**
     * 索引是否已构建
     */
    private boolean indexBuilt = false;
    
    /**
     * 在应用启动时构建索引
     */
    @PostConstruct
    public void buildIndex() {
        log.info("开始构建搜索索引...");
        long startTime = System.currentTimeMillis();
        
        List<CircuitDocument> documents = dataLoaderService.getAllDocuments();
        
        for (CircuitDocument doc : documents) {
            // 存储文档映射
            documentMap.put(doc.getId(), doc);
            
            // 构建各维度索引
            indexDocument(doc);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        indexBuilt = true;
        
        log.info("搜索索引构建完成，耗时 {}ms", duration);
        log.info("索引统计 - 品牌: {} 个, 型号: {} 个, ECU: {} 个, 部件: {} 个, 全文关键词: {} 个",
                brandIndex.size(), modelIndex.size(), ecuIndex.size(), 
                componentIndex.size(), fullTextIndex.size());
    }
    
    /**
     * 为单个文档构建索引
     */
    private void indexDocument(CircuitDocument doc) {
        String path = doc.getHierarchyPath();
        String fileName = doc.getFileName();
        String fullText = (path != null ? path : "") + " " + (fileName != null ? fileName : "");
        
        // 提取并索引品牌
        List<String> brands = keywordExtractor.extractBrands(fullText);
        for (String brand : brands) {
            brandIndex.computeIfAbsent(brand, k -> new HashSet<>()).add(doc.getId());
        }
        
        // 提取并索引 ECU 类型
        List<String> ecus = keywordExtractor.extractEcuTypes(fullText);
        for (String ecu : ecus) {
            ecuIndex.computeIfAbsent(ecu, k -> new HashSet<>()).add(doc.getId());
        }
        
        // 提取并索引部件
        List<String> components = keywordExtractor.extractComponents(fullText);
        for (String component : components) {
            componentIndex.computeIfAbsent(component, k -> new HashSet<>()).add(doc.getId());
        }
        
        // 从路径中提取型号（通常在路径的后几级）
        if (path != null) {
            String[] parts = path.split("->");
            for (String part : parts) {
                String trimmed = part.trim();
                // 型号通常包含数字或特定格式
                if (trimmed.matches(".*\\d+.*") || trimmed.length() <= 10) {
                    modelIndex.computeIfAbsent(trimmed, k -> new HashSet<>()).add(doc.getId());
                }
            }
        }
        
        // 构建全文索引（分词）
        String[] words = fullText.split("[\\s_\\->,，、。]+");
        for (String word : words) {
            if (word.length() >= 2) {
                fullTextIndex.computeIfAbsent(word, k -> new HashSet<>()).add(doc.getId());
            }
        }
    }
    
    /**
     * 按品牌搜索
     */
    public Set<Integer> searchByBrand(String brand) {
        if (brand == null || brand.isEmpty()) {
            return Collections.emptySet();
        }
        
        Set<Integer> results = new HashSet<>();
        
        // 获取品牌的所有变体
        List<String> variants = keywordExtractor.getVariants(brand);
        for (String variant : variants) {
            results.addAll(brandIndex.getOrDefault(variant, Collections.emptySet()));
        }
        
        return results;
    }
    
    /**
     * 按型号搜索
     */
    public Set<Integer> searchByModel(String model) {
        if (model == null || model.isEmpty()) {
            return Collections.emptySet();
        }
        
        Set<Integer> results = new HashSet<>();
        
        // 精确匹配
        results.addAll(modelIndex.getOrDefault(model, Collections.emptySet()));
        
        // 模糊匹配（型号包含查询词）
        for (Map.Entry<String, Set<Integer>> entry : modelIndex.entrySet()) {
            if (entry.getKey().contains(model) || model.contains(entry.getKey())) {
                results.addAll(entry.getValue());
            }
        }
        
        return results;
    }
    
    /**
     * 按 ECU 类型搜索
     */
    public Set<Integer> searchByEcu(String ecuType) {
        if (ecuType == null || ecuType.isEmpty()) {
            return Collections.emptySet();
        }
        
        Set<Integer> results = new HashSet<>();
        
        // 标准化 ECU 类型
        String normalized = keywordExtractor.normalize(ecuType);
        
        // 获取所有变体
        List<String> variants = keywordExtractor.getVariants(normalized);
        for (String variant : variants) {
            results.addAll(ecuIndex.getOrDefault(variant, Collections.emptySet()));
            results.addAll(ecuIndex.getOrDefault(variant.toUpperCase(), Collections.emptySet()));
        }
        
        return results;
    }
    
    /**
     * 按部件类型搜索
     */
    public Set<Integer> searchByComponent(String component) {
        if (component == null || component.isEmpty()) {
            return Collections.emptySet();
        }
        
        Set<Integer> results = new HashSet<>();
        
        // 标准化部件名称
        String normalized = keywordExtractor.normalize(component);
        
        // 精确匹配
        results.addAll(componentIndex.getOrDefault(component, Collections.emptySet()));
        results.addAll(componentIndex.getOrDefault(normalized, Collections.emptySet()));
        
        // 模糊匹配（部件名包含查询词或查询词包含部件名）
        for (Map.Entry<String, Set<Integer>> entry : componentIndex.entrySet()) {
            String key = entry.getKey();
            if (key.contains(component) || component.contains(key) ||
                key.contains(normalized) || normalized.contains(key)) {
                results.addAll(entry.getValue());
            }
        }
        
        return results;
    }
    
    /**
     * 全文搜索
     */
    public Set<Integer> searchFullText(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return Collections.emptySet();
        }
        
        Set<Integer> results = new HashSet<>();
        
        // 精确匹配
        results.addAll(fullTextIndex.getOrDefault(keyword, Collections.emptySet()));
        
        // 模糊匹配
        for (Map.Entry<String, Set<Integer>> entry : fullTextIndex.entrySet()) {
            if (entry.getKey().contains(keyword) || keyword.contains(entry.getKey())) {
                results.addAll(entry.getValue());
            }
        }
        
        return results;
    }
    
    /**
     * 基于 QueryInfo 进行交集搜索
     * 这是核心方法：只返回同时满足所有条件的文档
     * 优化：当某个维度搜索结果为空时，跳过该维度
     */
    public Set<Integer> searchIntersection(QueryInfo queryInfo) {
        if (queryInfo == null) {
            return Collections.emptySet();
        }
        
        List<Set<Integer>> resultSets = new ArrayList<>();
        
        // 按品牌搜索
        if (queryInfo.getBrand() != null && !queryInfo.getBrand().isEmpty() 
            && !"null".equals(queryInfo.getBrand())) {
            Set<Integer> brandResults = searchByBrand(queryInfo.getBrand());
            if (!brandResults.isEmpty()) {
                resultSets.add(brandResults);
            }
        }
        
        // 按型号搜索
        if (queryInfo.getModel() != null && !queryInfo.getModel().isEmpty()
            && !"null".equals(queryInfo.getModel())) {
            Set<Integer> modelResults = searchByModel(queryInfo.getModel());
            if (!modelResults.isEmpty()) {
                resultSets.add(modelResults);
            }
        }
        
        // 按 ECU 搜索
        if (queryInfo.getEcuType() != null && !queryInfo.getEcuType().isEmpty()
            && !"null".equals(queryInfo.getEcuType())) {
            Set<Integer> ecuResults = searchByEcu(queryInfo.getEcuType());
            if (!ecuResults.isEmpty()) {
                resultSets.add(ecuResults);
            }
        }
        
        // 按部件搜索
        if (queryInfo.getComponent() != null && !queryInfo.getComponent().isEmpty()
            && !"null".equals(queryInfo.getComponent())) {
            Set<Integer> componentResults = searchByComponent(queryInfo.getComponent());
            if (!componentResults.isEmpty()) {
                resultSets.add(componentResults);
            }
        }
        
        // 如果没有任何搜索条件，返回空
        if (resultSets.isEmpty()) {
            return Collections.emptySet();
        }
        
        // 计算交集
        Set<Integer> intersection = new HashSet<>(resultSets.get(0));
        for (int i = 1; i < resultSets.size(); i++) {
            intersection.retainAll(resultSets.get(i));
        }
        
        log.debug("交集搜索 - QueryInfo: {}, 各维度结果数: {}, 交集结果数: {}", 
                queryInfo, 
                resultSets.stream().map(Set::size).collect(Collectors.toList()),
                intersection.size());
        
        return intersection;
    }
    
    /**
     * 基于 QueryInfo 进行并集搜索（宽松模式）
     * 当交集搜索结果为空时使用
     */
    public Set<Integer> searchUnion(QueryInfo queryInfo) {
        if (queryInfo == null) {
            return Collections.emptySet();
        }
        
        Set<Integer> union = new HashSet<>();
        
        if (queryInfo.getBrand() != null) {
            union.addAll(searchByBrand(queryInfo.getBrand()));
        }
        if (queryInfo.getModel() != null) {
            union.addAll(searchByModel(queryInfo.getModel()));
        }
        if (queryInfo.getEcuType() != null) {
            union.addAll(searchByEcu(queryInfo.getEcuType()));
        }
        if (queryInfo.getComponent() != null) {
            union.addAll(searchByComponent(queryInfo.getComponent()));
        }
        
        return union;
    }
    
    /**
     * 根据文档ID获取文档
     */
    public CircuitDocument getDocument(Integer id) {
        return documentMap.get(id);
    }
    
    /**
     * 根据文档ID列表获取文档列表
     */
    public List<CircuitDocument> getDocuments(Collection<Integer> ids) {
        return ids.stream()
                .map(documentMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取索引统计信息
     */
    public Map<String, Integer> getIndexStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("totalDocuments", documentMap.size());
        stats.put("brandCount", brandIndex.size());
        stats.put("modelCount", modelIndex.size());
        stats.put("ecuCount", ecuIndex.size());
        stats.put("componentCount", componentIndex.size());
        stats.put("fullTextKeywords", fullTextIndex.size());
        return stats;
    }
    
    /**
     * 检查索引是否已构建
     */
    public boolean isIndexBuilt() {
        return indexBuilt;
    }
}
