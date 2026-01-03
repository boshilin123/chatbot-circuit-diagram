package com.bo.chatbot.service;

import com.bo.chatbot.model.CircuitDocument;
import com.bo.chatbot.model.Option;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 结果分类器
 * 当搜索结果较多时，分析结果并生成分类选项
 * 帮助用户通过选择题缩小范围
 */
@Slf4j
@Component
public class ResultCategorizer {
    
    /**
     * 最大选项数量
     */
    private static final int MAX_OPTIONS = 5;
    
    /**
     * 最小选项数量
     */
    private static final int MIN_OPTIONS = 2;
    
    /**
     * 分类结果
     */
    public static class CategoryResult {
        private String categoryType;  // 分类类型：brand/model/component/series
        private String prompt;        // 提示语
        private List<Option> options; // 选项列表
        private Map<String, List<CircuitDocument>> categoryMap; // 分类映射
        
        public CategoryResult(String categoryType, String prompt, List<Option> options,
                              Map<String, List<CircuitDocument>> categoryMap) {
            this.categoryType = categoryType;
            this.prompt = prompt;
            this.options = options;
            this.categoryMap = categoryMap;
        }
        
        public String getCategoryType() { return categoryType; }
        public String getPrompt() { return prompt; }
        public List<Option> getOptions() { return options; }
        public Map<String, List<CircuitDocument>> getCategoryMap() { return categoryMap; }
    }
    
    /**
     * 分析结果并生成分类选项
     * 优先级：品牌 > 型号系列 > 部件类型 > ECU类型
     * 避免重复使用已使用过的分类类型
     */
    public CategoryResult categorize(List<CircuitDocument> results, int totalCount) {
        return categorize(results, totalCount, new HashSet<>());
    }
    
    /**
     * 分析结果并生成分类选项（带已使用分类类型）
     */
    public CategoryResult categorize(List<CircuitDocument> results, int totalCount, Set<String> usedTypes) {
        if (results == null || results.size() <= 5) {
            return null;
        }
        
        log.debug("开始分类 - 结果数: {}, 已使用分类: {}", results.size(), usedTypes);
        
        // 1. 尝试按品牌分类（如果结果涉及多个品牌且未使用过）
        if (!usedTypes.contains("brand")) {
            CategoryResult byBrand = categorizeByBrand(results, totalCount);
            if (byBrand != null && byBrand.getOptions().size() >= MIN_OPTIONS) {
                log.debug("使用品牌分类，选项数: {}", byBrand.getOptions().size());
                return byBrand;
            }
        }
        
        // 2. 尝试按型号/系列分类
        if (!usedTypes.contains("model")) {
            CategoryResult byModel = categorizeByModelSeries(results, totalCount);
            if (byModel != null && byModel.getOptions().size() >= MIN_OPTIONS) {
                log.debug("使用型号分类，选项数: {}", byModel.getOptions().size());
                return byModel;
            }
        }
        
        // 3. 尝试按部件类型分类
        if (!usedTypes.contains("component")) {
            CategoryResult byComponent = categorizeByComponent(results, totalCount);
            if (byComponent != null && byComponent.getOptions().size() >= MIN_OPTIONS) {
                log.debug("使用部件分类，选项数: {}", byComponent.getOptions().size());
                return byComponent;
            }
        }
        
        // 4. 尝试按ECU类型分类
        if (!usedTypes.contains("ecu")) {
            CategoryResult byEcu = categorizeByEcu(results, totalCount);
            if (byEcu != null && byEcu.getOptions().size() >= MIN_OPTIONS) {
                log.debug("使用ECU分类，选项数: {}", byEcu.getOptions().size());
                return byEcu;
            }
        }
        
        log.debug("无法找到有效的分类方式");
        return null;
    }
    
    /**
     * 按品牌分类
     */
    private CategoryResult categorizeByBrand(List<CircuitDocument> results, int totalCount) {
        // 常见品牌列表
        String[] brands = {
            "三一", "徐工", "卡特", "小松", "日立", "神钢", "斗山", "现代", "沃尔沃",
            "东风", "解放", "重汽", "陕汽", "福田", "江淮", "北奔", "红岩", "华菱",
            "康明斯", "潍柴", "玉柴", "锡柴", "上柴", "朝柴",
            "博世", "电装", "德尔福", "大陆"
        };
        
        Map<String, List<CircuitDocument>> brandMap = new LinkedHashMap<>();
        
        for (CircuitDocument doc : results) {
            String text = (doc.getFileName() != null ? doc.getFileName() : "") + 
                         (doc.getHierarchyPath() != null ? doc.getHierarchyPath() : "");
            
            for (String brand : brands) {
                if (text.contains(brand)) {
                    brandMap.computeIfAbsent(brand, k -> new ArrayList<>()).add(doc);
                    break; // 每个文档只归入一个品牌
                }
            }
        }
        
        return buildCategoryResult(brandMap, totalCount, "brand", 
                "请问您需要哪个品牌的？");
    }
    
    /**
     * 按型号/系列分类（从文件名智能提取）
     * 确保分类互斥，每个文档只归入一个分类
     */
    private CategoryResult categorizeByModelSeries(List<CircuitDocument> results, int totalCount) {
        Map<String, List<CircuitDocument>> modelMap = new LinkedHashMap<>();
        
        for (CircuitDocument doc : results) {
            String model = extractModelFromFileName(doc.getFileName());
            if (model != null && !model.isEmpty()) {
                modelMap.computeIfAbsent(model, k -> new ArrayList<>()).add(doc);
            }
        }
        
        // 处理型号包含关系，确保互斥
        // 例如：如果有"天龙"和"天龙旗舰版"，"天龙旗舰版"的文档不应该出现在"天龙"中
        Map<String, List<CircuitDocument>> exclusiveMap = new LinkedHashMap<>();
        List<String> sortedModels = new ArrayList<>(modelMap.keySet());
        // 按长度降序排序，先处理更具体的型号
        sortedModels.sort((a, b) -> b.length() - a.length());
        
        Set<Integer> assignedDocIds = new HashSet<>();
        for (String model : sortedModels) {
            List<CircuitDocument> docs = modelMap.get(model);
            List<CircuitDocument> exclusiveDocs = new ArrayList<>();
            for (CircuitDocument doc : docs) {
                if (!assignedDocIds.contains(doc.getId())) {
                    exclusiveDocs.add(doc);
                    assignedDocIds.add(doc.getId());
                }
            }
            if (!exclusiveDocs.isEmpty()) {
                exclusiveMap.put(model, exclusiveDocs);
            }
        }
        
        return buildCategoryResult(exclusiveMap, totalCount, "model", 
                "请问您需要哪个型号/系列的？");
    }
    
    /**
     * 从文件名智能提取型号信息
     * 例如：
     * - "东风_天龙D320_BCM_针脚定义" -> "天龙D320"
     * - "东风_天龙旗舰版_仪表电路图" -> "天龙旗舰版"
     * - "东风_天龙_三代VECU_针脚定义" -> "天龙"
     */
    private String extractModelFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        
        // 尝试从下划线分隔的部分提取（优先）
        String[] parts = fileName.split("[_]");
        if (parts.length >= 2) {
            // 跳过品牌名（第一部分），取第二部分
            String candidate = parts[1].trim();
            // 过滤掉通用词
            if (!isGenericWord(candidate) && candidate.length() >= 2 && candidate.length() <= 20) {
                return candidate;
            }
            // 如果第二部分是通用词，尝试第三部分
            if (parts.length >= 3) {
                candidate = parts[2].trim();
                if (!isGenericWord(candidate) && candidate.length() >= 2 && candidate.length() <= 15) {
                    return candidate;
                }
            }
        }
        
        // 尝试提取英文型号模式（如 SY55, XE135G, KL, KC 等）
        Pattern modelPattern = Pattern.compile("([A-Z]{1,3}\\d{2,4}[A-Z]?|[A-Z]{2,3}系列?)");
        Matcher matcher = modelPattern.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
    
    /**
     * 判断是否是通用词（不适合作为分类）
     */
    private boolean isGenericWord(String word) {
        String[] genericWords = {
            "整车电路图", "电路图", "ECU电路图", "针脚定义", "线束图",
            "仪表", "保险丝", "传感器", "继电器", "发动机",
            "挖掘机", "装载机", "推土机", "压路机", "起重机",
            "重卡", "轻卡", "牵引车", "自卸车", "搅拌车"
        };
        for (String generic : genericWords) {
            if (word.contains(generic)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 按部件类型分类
     */
    private CategoryResult categorizeByComponent(List<CircuitDocument> results, int totalCount) {
        Map<String, List<CircuitDocument>> componentMap = new LinkedHashMap<>();
        
        // 部件类型及其关键词
        Map<String, String[]> componentKeywords = new LinkedHashMap<>();
        componentKeywords.put("整车电路图", new String[]{"整车电路图", "整车线路图", "全车电路"});
        componentKeywords.put("ECU/电脑板", new String[]{"ECU", "电脑板", "控制器", "ECM", "DCU"});
        componentKeywords.put("仪表/显示器", new String[]{"仪表", "显示器", "仪表盘", "组合仪表"});
        componentKeywords.put("保险丝盒", new String[]{"保险丝", "熔断器", "配电盒"});
        componentKeywords.put("针脚定义", new String[]{"针脚定义", "针脚图", "接口定义"});
        componentKeywords.put("线束图", new String[]{"线束", "接线图", "布线图"});
        
        for (CircuitDocument doc : results) {
            String text = (doc.getFileName() != null ? doc.getFileName() : "") + 
                         (doc.getHierarchyPath() != null ? doc.getHierarchyPath() : "");
            
            for (Map.Entry<String, String[]> entry : componentKeywords.entrySet()) {
                boolean matched = false;
                for (String keyword : entry.getValue()) {
                    if (text.contains(keyword)) {
                        componentMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(doc);
                        matched = true;
                        break;
                    }
                }
                if (matched) break;
            }
        }
        
        return buildCategoryResult(componentMap, totalCount, "component", 
                "请问您需要哪种类型的电路图？");
    }
    
    /**
     * 按ECU类型分类
     */
    private CategoryResult categorizeByEcu(List<CircuitDocument> results, int totalCount) {
        Map<String, List<CircuitDocument>> ecuMap = new LinkedHashMap<>();
        
        // ECU类型模式
        Pattern ecuPattern = Pattern.compile("(CM\\d{4}|EDC\\d{2}[A-Z]\\d{2}|WISE\\d{2}|博世[\\d.]+|电装|德尔福)");
        
        for (CircuitDocument doc : results) {
            String text = (doc.getFileName() != null ? doc.getFileName() : "") + 
                         (doc.getHierarchyPath() != null ? doc.getHierarchyPath() : "");
            
            Matcher matcher = ecuPattern.matcher(text);
            if (matcher.find()) {
                String ecu = matcher.group(1);
                ecuMap.computeIfAbsent(ecu, k -> new ArrayList<>()).add(doc);
            }
        }
        
        return buildCategoryResult(ecuMap, totalCount, "ecu", 
                "请问您需要哪种ECU类型的？");
    }
    
    /**
     * 构建分类结果
     * 要求分类覆盖率至少达到60%，否则返回null
     */
    private CategoryResult buildCategoryResult(Map<String, List<CircuitDocument>> categoryMap, 
                                                int totalCount, String categoryType, String promptTemplate) {
        // 过滤掉只有1个文档的分类，并按数量排序
        categoryMap = categoryMap.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .limit(MAX_OPTIONS)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
        
        if (categoryMap.size() < MIN_OPTIONS) {
            return null;
        }
        
        // 计算分类覆盖的文档数（这才是实际显示给用户的数量）
        int coveredCount = categoryMap.values().stream().mapToInt(List::size).sum();
        double coverageRate = (double) coveredCount / totalCount;
        
        // 如果覆盖率低于60%，认为这个分类方式不够好
        if (coverageRate < 0.6) {
            log.debug("分类覆盖率不足: {}%, 类型: {}", String.format("%.1f", coverageRate * 100), categoryType);
            return null;
        }
        
        List<Option> options = buildOptions(categoryMap);
        // 直接使用提示语模板，不进行数字格式化
        String prompt = promptTemplate;
        
        return new CategoryResult(categoryType, prompt, options, categoryMap);
    }
    
    /**
     * 构建选项列表
     */
    private List<Option> buildOptions(Map<String, List<CircuitDocument>> categoryMap) {
        List<Option> options = new ArrayList<>();
        int index = 1;
        
        for (Map.Entry<String, List<CircuitDocument>> entry : categoryMap.entrySet()) {
            String label = entry.getKey();
            int count = entry.getValue().size();
            
            // 选项显示：只显示类别名称，不显示数量
            String displayText = label;
            
            // value 存储分类键，用于后续筛选
            options.add(new Option(index++, displayText, "category:" + label));
        }
        
        return options;
    }
    
    /**
     * 根据分类选择筛选结果
     * 使用精确匹配，确保筛选结果正确
     */
    public List<CircuitDocument> filterByCategory(List<CircuitDocument> results, 
                                                   String categoryType, String categoryValue) {
        if (results == null || categoryValue == null) {
            return results;
        }
        
        // 对于型号分类，需要精确匹配
        if ("model".equals(categoryType)) {
            return results.stream()
                    .filter(doc -> {
                        String model = extractModelFromFileName(doc.getFileName());
                        return categoryValue.equals(model);
                    })
                    .collect(Collectors.toList());
        }
        
        // 其他分类使用包含匹配
        return results.stream()
                .filter(doc -> matchesCategory(doc, categoryType, categoryValue))
                .collect(Collectors.toList());
    }
    
    /**
     * 检查文档是否匹配分类
     * 使用精确匹配逻辑，避免"天龙"匹配到"天龙旗舰版"
     */
    private boolean matchesCategory(CircuitDocument doc, String categoryType, String categoryValue) {
        String fileName = doc.getFileName() != null ? doc.getFileName() : "";
        String path = doc.getHierarchyPath() != null ? doc.getHierarchyPath() : "";
        String text = fileName + path;
        
        switch (categoryType) {
            case "brand":
                // 品牌匹配：直接包含即可
                return text.contains(categoryValue);
                
            case "model":
                // 型号匹配：需要更精确的匹配
                // 提取该文档的型号，与选择的型号比较
                String docModel = extractModelFromFileName(fileName);
                if (categoryValue.equals(docModel)) {
                    return true;
                }
                // 如果提取的型号不匹配，检查文件名是否精确包含该型号
                // 但要排除更长的型号（如选择"天龙"不应匹配"天龙旗舰版"）
                if (text.contains(categoryValue)) {
                    // 检查是否有更具体的型号
                    String[] longerModels = {"天龙旗舰版", "天龙KL", "天龙KC", "天龙VL", "天龙D320"};
                    for (String longer : longerModels) {
                        if (longer.startsWith(categoryValue) && !longer.equals(categoryValue) && text.contains(longer)) {
                            return false; // 有更具体的型号，不匹配
                        }
                    }
                    return true;
                }
                return false;
                
            case "component":
                // 部件匹配 - 改进逻辑，支持互斥分类
                return matchesComponentCategory(text, categoryValue);
                
            case "ecu":
                return text.contains(categoryValue);
                
            default:
                return text.contains(categoryValue);
        }
    }
    
    /**
     * 部件分类匹配逻辑（支持互斥分类）
     */
    private boolean matchesComponentCategory(String text, String categoryValue) {
        switch (categoryValue) {
            case "ECU/电脑板":
                // ECU相关关键词
                String[] ecuKeywords = {"ECU", "电脑板", "控制器", "ECM", "DCU", "BCM", "VECU", "VCU", "TCU"};
                return Arrays.stream(ecuKeywords).anyMatch(text::contains);
                
            case "仪表/显示器":
                // 仪表相关关键词，但排除ECU相关
                String[] meterKeywords = {"仪表", "显示器", "仪表盘", "组合仪表"};
                String[] excludeKeywords = {"ECU", "电脑板", "控制器", "ECM", "DCU", "BCM", "VECU", "VCU", "TCU"};
                
                // 必须包含仪表关键词
                boolean hasMeterKeyword = Arrays.stream(meterKeywords).anyMatch(text::contains);
                // 不能包含ECU关键词
                boolean hasEcuKeyword = Arrays.stream(excludeKeywords).anyMatch(text::contains);
                
                return hasMeterKeyword && !hasEcuKeyword;
                
            case "保险丝盒":
                String[] fuseKeywords = {"保险丝", "熔断器", "配电盒", "保险盒"};
                return Arrays.stream(fuseKeywords).anyMatch(text::contains);
                
            case "针脚定义":
                String[] pinKeywords = {"针脚定义", "针脚图", "接口定义", "插头定义"};
                return Arrays.stream(pinKeywords).anyMatch(text::contains);
                
            case "线束图":
                String[] wireKeywords = {"线束", "接线图", "布线图", "线路图"};
                return Arrays.stream(wireKeywords).anyMatch(text::contains);
                
            case "整车电路图":
                String[] vehicleKeywords = {"整车电路图", "整车线路图", "全车电路"};
                return Arrays.stream(vehicleKeywords).anyMatch(text::contains);
                
            default:
                // 默认使用包含匹配
                if (categoryValue.contains("/")) {
                    return Arrays.stream(categoryValue.split("/")).anyMatch(text::contains);
                }
                return text.contains(categoryValue);
        }
    }
}
