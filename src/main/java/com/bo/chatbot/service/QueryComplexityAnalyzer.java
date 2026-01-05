package com.bo.chatbot.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 查询复杂度分析器
 * 判断用户查询是否需要AI理解，还是可以直接使用本地关键词提取
 */
@Slf4j
@Service
public class QueryComplexityAnalyzer {
    
    @Autowired
    private KeywordExtractor keywordExtractor;
    
    /**
     * 简单查询的特征模式
     */
    private static final Pattern SIMPLE_BRAND_MODEL_PATTERN = Pattern.compile(
        ".*(东风|解放|重汽|陕汽|福田|江淮|红岩|华菱|北奔|三一|徐工|卡特|小松|日立|神钢|斗山|现代|沃尔沃|康明斯|潍柴|玉柴|锡柴|上柴|朝柴|博世|电装|德尔福|大陆).*(天龙|杰狮|德龙|奥龙|欧曼|格尔发|乘龙|霸龙|4HK1|6HK1|C240|C280|C300|C350|C53|C63|C81|M11|ISM|QSM|WP10|WP12|YC6K|YC6M|CA6DM|CA6DF|EDC7|EDC17|CM2150|CM2250|CM2350|CM570|CM871|CM2880).*"
    );
    
    /**
     * 明确的部件关键词
     */
    private static final Set<String> CLEAR_COMPONENT_KEYWORDS = new HashSet<>(Arrays.asList(
        "ECU", "电脑板", "控制器", "仪表", "保险丝", "保险盒", "接线盒", "配电盒",
        "针脚定义", "针脚图", "接口定义", "线束", "接线图", "布线图", "传感器",
        "继电器", "开关", "电机", "泵", "阀", "喷油器", "点火线圈"
    ));
    
    /**
     * 明确的资料类型关键词
     */
    private static final Set<String> CLEAR_DOCUMENT_KEYWORDS = new HashSet<>(Arrays.asList(
        "电路图", "线路图", "图纸", "资料", "定义图", "针脚图", "接线图", "布线图"
    ));
    
    /**
     * 复杂查询的特征词（降低权重，因为很多是礼貌用语）
     */
    private static final Set<String> COMPLEX_QUERY_INDICATORS = new HashSet<>(Arrays.asList(
        "怎么", "如何", "为什么", "什么", "哪个", "哪种", "哪里", "多少", "几个",
        "不知道", "不清楚", "不确定", "可能", "大概", "应该", "或者", "还是"
    ));
    
    /**
     * 礼貌用语（不应该增加复杂度）
     */
    private static final Set<String> POLITE_EXPRESSIONS = new HashSet<>(Arrays.asList(
        "帮我", "请问", "想要", "需要", "寻找", "查找", "搜索", "找一下",
        "请帮忙", "麻烦", "谢谢", "请", "能否", "可以"
    ));
    
    /**
     * 模糊表达词
     */
    private static final Set<String> VAGUE_EXPRESSIONS = new HashSet<>(Arrays.asList(
        "那个", "这个", "某个", "一些", "一种", "类似", "差不多", "大致", "左右",
        "之类", "什么的", "等等", "相关", "有关", "关于"
    ));
    
    /**
     * 分析查询复杂度
     */
    public QueryComplexityResult analyzeComplexity(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new QueryComplexityResult(true, "空查询", 0);
        }
        
        String normalizedQuery = query.trim().toLowerCase();
        int complexityScore = 0;
        StringBuilder reason = new StringBuilder();
        
        // 1. 检查查询长度
        if (normalizedQuery.length() < 4) {
            complexityScore += 30;
            reason.append("查询过短; ");
        } else if (normalizedQuery.length() > 50) {
            complexityScore += 20;
            reason.append("查询过长; ");
        }
        
        // 2. 检查关键词提取结果（优先级最高）
        List<String> brands = keywordExtractor.extractBrands(query);
        List<String> ecus = keywordExtractor.extractEcuTypes(query);
        List<String> components = keywordExtractor.extractComponents(query);
        
        int keywordCount = brands.size() + ecus.size() + components.size();
        
        // 特殊处理：如果查询就是一个设备类型（如"挖掘机"），认为是简单查询
        if (keywordCount == 1 && components.size() == 1 && isEquipmentType(query)) {
            complexityScore -= 50; // 大幅降低复杂度
            reason.append("单一设备类型查询; ");
        } else if (keywordCount >= 2) {
            complexityScore -= 40; // 增加权重
            reason.append("关键词丰富(").append(keywordCount).append("个); ");
        } else if (keywordCount == 1 && (!brands.isEmpty() || !ecus.isEmpty())) {
            // 有品牌或ECU信息也降低复杂度
            complexityScore -= 20;
            reason.append("有明确品牌/ECU; ");
        } else if (keywordCount == 0) {
            complexityScore += 35;
            reason.append("无明确关键词; ");
        }
        
        // 3. 检查是否包含明确的部件或资料类型
        boolean hasComponent = CLEAR_COMPONENT_KEYWORDS.stream()
                .anyMatch(normalizedQuery::contains);
        boolean hasDocType = CLEAR_DOCUMENT_KEYWORDS.stream()
                .anyMatch(normalizedQuery::contains);
        
        if (hasComponent || hasDocType) {
            complexityScore -= 25; // 增加权重
            reason.append("明确部件/资料类型; ");
        }
        
        // 4. 检查是否有明确的品牌+型号组合
        if (SIMPLE_BRAND_MODEL_PATTERN.matcher(normalizedQuery).matches()) {
            complexityScore -= 30;
            reason.append("明确品牌型号组合; ");
        }
        
        // 5. 检查是否包含复杂查询指示词（但权重降低）
        boolean hasComplexIndicator = false;
        for (String indicator : COMPLEX_QUERY_INDICATORS) {
            if (normalizedQuery.contains(indicator)) {
                // 如果已经有明确关键词，复杂指示词的权重降低
                if (keywordCount >= 1 || hasComponent) {
                    complexityScore += 10; // 进一步降低权重
                } else {
                    complexityScore += 40; // 原权重
                }
                reason.append("包含复杂指示词(").append(indicator).append("); ");
                hasComplexIndicator = true;
                break;
            }
        }
        
        // 6. 检查礼貌用语（不增加复杂度，甚至可能降低）
        for (String polite : POLITE_EXPRESSIONS) {
            if (normalizedQuery.contains(polite)) {
                // 如果有明确关键词，礼貌用语实际上降低复杂度
                if (keywordCount >= 1 || hasComponent) {
                    complexityScore -= 10;
                    reason.append("礼貌用语+明确关键词; ");
                } else {
                    // 没有明确关键词时，礼貌用语稍微增加复杂度
                    complexityScore += 10;
                    reason.append("仅礼貌用语; ");
                }
                break;
            }
        }
        
        // 7. 检查是否包含模糊表达
        for (String vague : VAGUE_EXPRESSIONS) {
            if (normalizedQuery.contains(vague)) {
                // 如果已经有明确关键词，模糊表达的权重降低
                if (keywordCount >= 1 || hasComponent) {
                    complexityScore += 5; // 进一步降低权重
                } else {
                    complexityScore += 25; // 原权重
                }
                reason.append("包含模糊表达(").append(vague).append("); ");
                break;
            }
        }
        
        // 8. 检查特殊字符和数字
        if (normalizedQuery.matches(".*[0-9]+.*")) {
            complexityScore -= 10;
            reason.append("包含数字; ");
        }
        
        // 9. 检查是否是纯英文/数字组合（通常是型号）
        if (normalizedQuery.matches("^[a-zA-Z0-9\\s]+$") && normalizedQuery.length() <= 20) {
            complexityScore -= 25;
            reason.append("纯英文数字组合; ");
        }
        
        // 10. 特殊规则：如果是口语化但包含明确关键词的查询，倾向于本地处理
        if ((hasComplexIndicator || normalizedQuery.contains("帮我") || normalizedQuery.contains("找一下")) 
            && keywordCount >= 1) {
            complexityScore -= 25;
            reason.append("口语化但关键词明确; ");
        }
        
        // 判断是否需要AI处理（降低阈值，让更多查询使用本地处理）
        boolean needsAI = complexityScore > 20;
        
        log.debug("查询复杂度分析 - Query: '{}', Score: {}, NeedsAI: {}, Reason: {}", 
                query, complexityScore, needsAI, reason.toString());
        
        return new QueryComplexityResult(needsAI, reason.toString(), complexityScore);
    }
    
    /**
     * 判断是否是设备类型关键词
     */
    private boolean isEquipmentType(String query) {
        String[] equipmentTypes = {"挖掘机", "装载机", "推土机", "压路机", "起重机", "搅拌车", "自卸车", "牵引车", "载货车", "客车", "轻卡", "重卡"};
        String normalizedQuery = query.trim().toLowerCase();
        for (String type : equipmentTypes) {
            if (normalizedQuery.equals(type) || normalizedQuery.contains(type)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查是否是明显的简单查询
     */
    public boolean isObviouslySimple(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String normalizedQuery = query.trim().toLowerCase();
        
        // 1. 长度在合理范围内
        if (normalizedQuery.length() < 4 || normalizedQuery.length() > 30) {
            return false;
        }
        
        // 2. 不包含复杂指示词
        for (String indicator : COMPLEX_QUERY_INDICATORS) {
            if (normalizedQuery.contains(indicator)) {
                return false;
            }
        }
        
        // 3. 不包含模糊表达
        for (String vague : VAGUE_EXPRESSIONS) {
            if (normalizedQuery.contains(vague)) {
                return false;
            }
        }
        
        // 4. 包含明确的关键词
        List<String> brands = keywordExtractor.extractBrands(query);
        List<String> ecus = keywordExtractor.extractEcuTypes(query);
        List<String> components = keywordExtractor.extractComponents(query);
        
        int keywordCount = brands.size() + ecus.size() + components.size();
        
        // 5. 至少有2个明确关键词，或者有品牌+部件组合
        return keywordCount >= 2 || (!brands.isEmpty() && !components.isEmpty());
    }
    
    /**
     * 查询复杂度分析结果
     */
    @Data
    public static class QueryComplexityResult {
        private final boolean needsAI;
        private final String reason;
        private final int complexityScore;
        
        public QueryComplexityResult(boolean needsAI, String reason, int complexityScore) {
            this.needsAI = needsAI;
            this.reason = reason;
            this.complexityScore = complexityScore;
        }
    }
}