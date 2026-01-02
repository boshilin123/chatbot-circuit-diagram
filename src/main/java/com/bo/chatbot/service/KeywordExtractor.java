package com.bo.chatbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 关键词提取器
 * 负责关键词标准化、同义词映射、从文本中提取关键词
 */
@Slf4j
@Component
public class KeywordExtractor {
    
    /**
     * 同义词映射表
     * 用于处理错别字、简称、别名等
     */
    private static final Map<String, String> SYNONYMS = new HashMap<>();
    
    /**
     * 品牌列表（用于从文本中提取品牌）
     */
    private static final Set<String> BRANDS = new HashSet<>();
    
    /**
     * ECU 类型列表
     */
    private static final Set<String> ECU_TYPES = new HashSet<>();
    
    /**
     * 部件类型列表
     */
    private static final Set<String> COMPONENTS = new HashSet<>();
    
    static {
        // 初始化同义词映射表
        initSynonyms();
        // 初始化品牌列表
        initBrands();
        // 初始化 ECU 类型列表
        initEcuTypes();
        // 初始化部件类型列表
        initComponents();
    }
    
    /**
     * 初始化同义词映射表
     */
    private static void initSynonyms() {
        // 错别字映射
        SYNONYMS.put("小忪", "小松");
        SYNONYMS.put("小淞", "小松");
        SYNONYMS.put("庆龄", "庆铃");
        SYNONYMS.put("庆玲", "庆铃");
        SYNONYMS.put("上气", "上汽");
        SYNONYMS.put("许工", "徐工");
        
        // ECU 型号映射
        SYNONYMS.put("2880", "CM2880");
        SYNONYMS.put("cm2880", "CM2880");
        SYNONYMS.put("edc7", "EDC7");
        SYNONYMS.put("edc17", "EDC17");
        SYNONYMS.put("dcm3.7", "DCM3.7");
        SYNONYMS.put("c81", "EDC17C81");
        SYNONYMS.put("C81", "EDC17C81");
        
        // 品牌别名
        SYNONYMS.put("卡特", "卡特彼勒");
        SYNONYMS.put("cat", "卡特彼勒");
        SYNONYMS.put("CAT", "卡特彼勒");
        SYNONYMS.put("31", "三一");
        SYNONYMS.put("sany", "三一");
        SYNONYMS.put("SANY", "三一");
        
        // 部件同义词
        SYNONYMS.put("玻璃升降器", "玻璃升降");
        SYNONYMS.put("刹车开关", "制动");
        SYNONYMS.put("电脑版", "电脑板");
        SYNONYMS.put("针角", "针脚");
    }
    
    /**
     * 初始化品牌列表
     */
    private static void initBrands() {
        BRANDS.addAll(Arrays.asList(
            "三一", "徐工", "卡特彼勒", "小松", "日立", "沃尔沃", "斗山", "现代",
            "红岩", "上汽红岩", "重汽", "重汽王牌", "解放", "东风", "福田", "欧曼", "陕汽", "江淮",
            "康明斯", "博世", "电装", "德尔福", "潍柴", "玉柴", "锡柴",
            "庆铃", "五十铃", "日野", "三菱", "住友", "神钢",
            "豪瀚", "豪沃", "乘龙", "天龙", "大通", "柳工", "龙工", "临工",
            "雷沃", "山推", "厦工", "力士德", "詹阳", "山河智能"
        ));
    }
    
    /**
     * 初始化 ECU 类型列表
     */
    private static void initEcuTypes() {
        ECU_TYPES.addAll(Arrays.asList(
            "CM2880", "CM870", "CM2150", "CM2250", "CM2350",
            "EDC7", "EDC17", "EDC7UC31", "EDC7UC32",
            "DCM3.7", "DCM3.8", "DCM6.2",
            "MD1", "MD1CC878", "MD1CE",
            "ECM", "ECU", "VCU", "TCU"
        ));
    }
    
    /**
     * 初始化部件类型列表
     */
    private static void initComponents() {
        COMPONENTS.addAll(Arrays.asList(
            // 电气部件
            "保险丝", "保险盒", "仪表", "显示器", "传感器", "继电器",
            "电脑板", "控制器", "线束", "接头", "插头", "针脚", "接线盒",
            // 车身部件
            "玻璃升降", "玻璃升降器", "空调", "灯光", "雨刮", "喇叭", "门锁", "门窗",
            // 动力系统
            "液压", "油泵", "油箱", "滤芯", "轨压", "尿素", "供电",
            "发动机", "变速箱", "差速器", "制动", "刹车", "刹车开关", "ABS",
            // ECU相关
            "ECU", "ECM", "VCU", "TCU", "BCM"
        ));
    }
    
    /**
     * 标准化关键词
     * 处理同义词映射
     * 
     * @param keyword 原始关键词
     * @return 标准化后的关键词
     */
    public String normalize(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = keyword.trim();
        
        // 先尝试精确匹配
        if (SYNONYMS.containsKey(trimmed)) {
            return SYNONYMS.get(trimmed);
        }
        
        // 再尝试小写匹配
        String lower = trimmed.toLowerCase();
        if (SYNONYMS.containsKey(lower)) {
            return SYNONYMS.get(lower);
        }
        
        return trimmed;
    }
    
    /**
     * 从文本中提取品牌
     * 
     * @param text 文本
     * @return 品牌列表
     */
    public List<String> extractBrands(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> found = new ArrayList<>();
        String normalizedText = text;
        
        // 先做同义词替换
        for (Map.Entry<String, String> entry : SYNONYMS.entrySet()) {
            if (normalizedText.contains(entry.getKey())) {
                normalizedText = normalizedText.replace(entry.getKey(), entry.getValue());
            }
        }
        
        // 查找品牌
        for (String brand : BRANDS) {
            if (normalizedText.contains(brand)) {
                found.add(brand);
            }
        }
        
        return found;
    }
    
    /**
     * 从文本中提取 ECU 类型
     * 
     * @param text 文本
     * @return ECU 类型列表
     */
    public List<String> extractEcuTypes(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> found = new ArrayList<>();
        String upperText = text.toUpperCase();
        
        for (String ecu : ECU_TYPES) {
            if (upperText.contains(ecu.toUpperCase())) {
                found.add(ecu);
            }
        }
        
        // 特殊处理：提取纯数字型号（如 2880）
        Pattern pattern = Pattern.compile("\\b(\\d{4})\\b");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String num = matcher.group(1);
            String mapped = normalize(num);
            if (!mapped.equals(num) && !found.contains(mapped)) {
                found.add(mapped);
            }
        }
        
        return found;
    }
    
    /**
     * 从文本中提取部件类型
     * 
     * @param text 文本
     * @return 部件类型列表
     */
    public List<String> extractComponents(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> found = new ArrayList<>();
        
        for (String component : COMPONENTS) {
            if (text.contains(component)) {
                found.add(component);
            }
        }
        
        return found;
    }
    
    /**
     * 从文本中提取所有关键词
     * 
     * @param text 文本
     * @return 关键词集合
     */
    public Set<String> extractAllKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        
        keywords.addAll(extractBrands(text));
        keywords.addAll(extractEcuTypes(text));
        keywords.addAll(extractComponents(text));
        
        return keywords;
    }
    
    /**
     * 检查文本是否包含指定关键词（支持同义词）
     * 
     * @param text 文本
     * @param keyword 关键词
     * @return 是否包含
     */
    public boolean containsKeyword(String text, String keyword) {
        if (text == null || keyword == null) {
            return false;
        }
        
        // 直接包含
        if (text.contains(keyword)) {
            return true;
        }
        
        // 标准化后包含
        String normalized = normalize(keyword);
        if (!normalized.equals(keyword) && text.contains(normalized)) {
            return true;
        }
        
        // 检查同义词
        for (Map.Entry<String, String> entry : SYNONYMS.entrySet()) {
            if (entry.getValue().equals(keyword) && text.contains(entry.getKey())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 获取关键词的所有变体（包括同义词）
     * 
     * @param keyword 关键词
     * @return 变体列表
     */
    public List<String> getVariants(String keyword) {
        List<String> variants = new ArrayList<>();
        variants.add(keyword);
        
        // 添加标准化形式
        String normalized = normalize(keyword);
        if (!normalized.equals(keyword)) {
            variants.add(normalized);
        }
        
        // 添加所有映射到该关键词的同义词
        for (Map.Entry<String, String> entry : SYNONYMS.entrySet()) {
            if (entry.getValue().equals(keyword) || entry.getValue().equals(normalized)) {
                if (!variants.contains(entry.getKey())) {
                    variants.add(entry.getKey());
                }
            }
        }
        
        return variants;
    }
}
