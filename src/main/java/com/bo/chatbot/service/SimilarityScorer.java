package com.bo.chatbot.service;

import com.bo.chatbot.model.CircuitDocument;
import com.bo.chatbot.model.QueryInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 相似度评分器
 * 根据 QueryInfo 和文档内容计算匹配分数
 * 
 * 评分权重：
 * - 品牌匹配：40%
 * - 型号匹配：30%
 * - 部件匹配：20%
 * - ECU匹配：10%
 */
@Slf4j
@Component
public class SimilarityScorer {
    
    /**
     * 评分权重常量
     */
    private static final double BRAND_WEIGHT = 0.40;
    private static final double MODEL_WEIGHT = 0.30;
    private static final double COMPONENT_WEIGHT = 0.20;
    private static final double ECU_WEIGHT = 0.10;
    
    /**
     * 精确匹配得分
     */
    private static final double EXACT_MATCH_SCORE = 1.0;
    
    /**
     * 部分匹配得分
     */
    private static final double PARTIAL_MATCH_SCORE = 0.6;
    
    /**
     * 模糊匹配得分
     */
    private static final double FUZZY_MATCH_SCORE = 0.3;
    
    @Autowired
    private KeywordExtractor keywordExtractor;
    
    /**
     * 计算文档与查询的相似度分数
     * 
     * @param doc 文档
     * @param queryInfo 查询信息
     * @return 相似度分数（0-100）
     */
    public double calculateScore(CircuitDocument doc, QueryInfo queryInfo) {
        if (doc == null || queryInfo == null) {
            return 0;
        }
        
        // 获取文档的全文内容
        String fullText = buildFullText(doc);
        
        double totalScore = 0;
        double maxPossibleScore = 0;
        
        // 计算品牌匹配分数
        if (queryInfo.getBrand() != null && !queryInfo.getBrand().isEmpty()) {
            double brandScore = calculateFieldScore(fullText, queryInfo.getBrand());
            totalScore += brandScore * BRAND_WEIGHT;
            maxPossibleScore += BRAND_WEIGHT;
        }
        
        // 计算型号匹配分数
        if (queryInfo.getModel() != null && !queryInfo.getModel().isEmpty()) {
            double modelScore = calculateFieldScore(fullText, queryInfo.getModel());
            totalScore += modelScore * MODEL_WEIGHT;
            maxPossibleScore += MODEL_WEIGHT;
        }
        
        // 计算部件匹配分数
        if (queryInfo.getComponent() != null && !queryInfo.getComponent().isEmpty()) {
            double componentScore = calculateFieldScore(fullText, queryInfo.getComponent());
            totalScore += componentScore * COMPONENT_WEIGHT;
            maxPossibleScore += COMPONENT_WEIGHT;
        }
        
        // 计算 ECU 匹配分数
        if (queryInfo.getEcuType() != null && !queryInfo.getEcuType().isEmpty()) {
            double ecuScore = calculateFieldScore(fullText, queryInfo.getEcuType());
            totalScore += ecuScore * ECU_WEIGHT;
            maxPossibleScore += ECU_WEIGHT;
        }
        
        // 如果没有任何字段匹配，使用原始查询进行全文匹配
        if (maxPossibleScore == 0 && queryInfo.getOriginalQuery() != null) {
            double fullTextScore = calculateFieldScore(fullText, queryInfo.getOriginalQuery());
            totalScore = fullTextScore;
            maxPossibleScore = 1.0;
        }
        
        // 归一化分数到 0-100
        if (maxPossibleScore > 0) {
            return (totalScore / maxPossibleScore) * 100;
        }
        
        return 0;
    }

    /**
     * 计算单个字段的匹配分数
     * 
     * @param text 文档文本
     * @param keyword 查询关键词
     * @return 匹配分数（0-1）
     */
    private double calculateFieldScore(String text, String keyword) {
        if (text == null || keyword == null || keyword.isEmpty()) {
            return 0;
        }
        
        String lowerText = text.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        
        // 1. 精确匹配（完全包含）
        if (lowerText.contains(lowerKeyword)) {
            return EXACT_MATCH_SCORE;
        }
        
        // 2. 标准化后匹配（处理同义词）
        String normalizedKeyword = keywordExtractor.normalize(keyword);
        if (!normalizedKeyword.equals(keyword) && lowerText.contains(normalizedKeyword.toLowerCase())) {
            return EXACT_MATCH_SCORE;
        }
        
        // 3. 检查关键词的变体
        for (String variant : keywordExtractor.getVariants(keyword)) {
            if (lowerText.contains(variant.toLowerCase())) {
                return PARTIAL_MATCH_SCORE;
            }
        }
        
        // 4. 特殊处理：对于通用设备类型关键词（如"挖掘机"），降低匹配要求
        if (isGenericEquipmentType(keyword)) {
            // 检查是否包含相关的设备类型词汇
            String[] equipmentIndicators = {"挖掘", "挖机", "挖土机", "excavator"};
            for (String indicator : equipmentIndicators) {
                if (lowerText.contains(indicator)) {
                    return PARTIAL_MATCH_SCORE;
                }
            }
        }
        
        // 5. 部分匹配（关键词的一部分出现在文本中）
        if (keyword.length() >= 2) {
            // 检查关键词的子串
            for (int i = 0; i < keyword.length() - 1; i++) {
                String subKeyword = keyword.substring(i, Math.min(i + 2, keyword.length()));
                if (lowerText.contains(subKeyword.toLowerCase())) {
                    return FUZZY_MATCH_SCORE;
                }
            }
        }
        
        // 6. 模糊匹配（文本的一部分出现在关键词中）
        String[] words = text.split("[\\s_\\->,，、。]+");
        for (String word : words) {
            if (word.length() >= 2 && lowerKeyword.contains(word.toLowerCase())) {
                return FUZZY_MATCH_SCORE;
            }
        }
        
        return 0;
    }
    
    /**
     * 判断是否是通用设备类型关键词
     */
    private boolean isGenericEquipmentType(String keyword) {
        String[] genericTypes = {"挖掘机", "装载机", "推土机", "压路机", "起重机", "搅拌车", "自卸车"};
        for (String type : genericTypes) {
            if (keyword.contains(type)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 构建文档的全文内容
     */
    private String buildFullText(CircuitDocument doc) {
        StringBuilder sb = new StringBuilder();
        
        if (doc.getHierarchyPath() != null) {
            sb.append(doc.getHierarchyPath()).append(" ");
        }
        if (doc.getFileName() != null) {
            sb.append(doc.getFileName()).append(" ");
        }
        
        return sb.toString();
    }
    
    /**
     * 计算带详细信息的分数（用于调试）
     */
    public ScoreDetail calculateScoreWithDetail(CircuitDocument doc, QueryInfo queryInfo) {
        ScoreDetail detail = new ScoreDetail();
        detail.setDocId(doc.getId());
        detail.setFileName(doc.getFileName());
        
        String fullText = buildFullText(doc);
        
        if (queryInfo.getBrand() != null && !queryInfo.getBrand().isEmpty()) {
            double score = calculateFieldScore(fullText, queryInfo.getBrand());
            detail.setBrandScore(score);
            detail.setBrandMatched(score > 0);
        }
        
        if (queryInfo.getModel() != null && !queryInfo.getModel().isEmpty()) {
            double score = calculateFieldScore(fullText, queryInfo.getModel());
            detail.setModelScore(score);
            detail.setModelMatched(score > 0);
        }
        
        if (queryInfo.getComponent() != null && !queryInfo.getComponent().isEmpty()) {
            double score = calculateFieldScore(fullText, queryInfo.getComponent());
            detail.setComponentScore(score);
            detail.setComponentMatched(score > 0);
        }
        
        if (queryInfo.getEcuType() != null && !queryInfo.getEcuType().isEmpty()) {
            double score = calculateFieldScore(fullText, queryInfo.getEcuType());
            detail.setEcuScore(score);
            detail.setEcuMatched(score > 0);
        }
        
        detail.setTotalScore(calculateScore(doc, queryInfo));
        
        return detail;
    }
    
    /**
     * 分数详情类（用于调试和分析）
     */
    public static class ScoreDetail {
        private Integer docId;
        private String fileName;
        private double brandScore;
        private double modelScore;
        private double componentScore;
        private double ecuScore;
        private double totalScore;
        private boolean brandMatched;
        private boolean modelMatched;
        private boolean componentMatched;
        private boolean ecuMatched;
        
        // Getters and Setters
        public Integer getDocId() { return docId; }
        public void setDocId(Integer docId) { this.docId = docId; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public double getBrandScore() { return brandScore; }
        public void setBrandScore(double brandScore) { this.brandScore = brandScore; }
        
        public double getModelScore() { return modelScore; }
        public void setModelScore(double modelScore) { this.modelScore = modelScore; }
        
        public double getComponentScore() { return componentScore; }
        public void setComponentScore(double componentScore) { this.componentScore = componentScore; }
        
        public double getEcuScore() { return ecuScore; }
        public void setEcuScore(double ecuScore) { this.ecuScore = ecuScore; }
        
        public double getTotalScore() { return totalScore; }
        public void setTotalScore(double totalScore) { this.totalScore = totalScore; }
        
        public boolean isBrandMatched() { return brandMatched; }
        public void setBrandMatched(boolean brandMatched) { this.brandMatched = brandMatched; }
        
        public boolean isModelMatched() { return modelMatched; }
        public void setModelMatched(boolean modelMatched) { this.modelMatched = modelMatched; }
        
        public boolean isComponentMatched() { return componentMatched; }
        public void setComponentMatched(boolean componentMatched) { this.componentMatched = componentMatched; }
        
        public boolean isEcuMatched() { return ecuMatched; }
        public void setEcuMatched(boolean ecuMatched) { this.ecuMatched = ecuMatched; }
        
        @Override
        public String toString() {
            return String.format("ScoreDetail{docId=%d, total=%.1f, brand=%.2f(%s), model=%.2f(%s), component=%.2f(%s), ecu=%.2f(%s)}",
                    docId, totalScore,
                    brandScore, brandMatched ? "✓" : "✗",
                    modelScore, modelMatched ? "✓" : "✗",
                    componentScore, componentMatched ? "✓" : "✗",
                    ecuScore, ecuMatched ? "✓" : "✗");
        }
    }
}
