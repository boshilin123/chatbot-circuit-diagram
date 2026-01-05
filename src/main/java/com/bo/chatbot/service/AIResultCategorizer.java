package com.bo.chatbot.service;

import com.bo.chatbot.model.CircuitDocument;
import com.bo.chatbot.model.Option;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI驱动的结果分类器
 * 使用DeepSeek API进行智能分类
 */
@Slf4j
@Service
public class AIResultCategorizer {
    
    @Value("${deepseek.api.key:}")
    private String apiKey;
    
    @Value("${deepseek.api.url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;
    
    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * AI分类结果
     */
    public static class AICategoryResult {
        private String prompt;
        private List<Option> options;
        private Map<String, List<CircuitDocument>> categoryMap;
        
        public AICategoryResult(String prompt, List<Option> options, Map<String, List<CircuitDocument>> categoryMap) {
            this.prompt = prompt;
            this.options = options;
            this.categoryMap = categoryMap;
        }
        
        public String getPrompt() { return prompt; }
        public List<Option> getOptions() { return options; }
        public Map<String, List<CircuitDocument>> getCategoryMap() { return categoryMap; }
    }
    
    /**
     * 使用AI进行智能分类
     */
    public AICategoryResult categorizeWithAI(List<CircuitDocument> results, String originalQuery) {
        if (results == null || results.size() <= 5) {
            log.debug("结果数量不足，跳过AI分类: {}", results != null ? results.size() : 0);
            return null;
        }
        
        log.info("开始AI分类 - 查询: '{}', 结果数: {}", originalQuery, results.size());
        
        try {
            // 1. 构建文档摘要
            String documentSummary = buildDocumentSummary(results);
            log.debug("文档摘要构建完成，长度: {}", documentSummary.length());
            
            // 2. 构建AI提示词
            String prompt = buildCategorizePrompt(originalQuery, documentSummary, results.size());
            log.debug("AI提示词构建完成，长度: {}", prompt.length());
            
            // 3. 调用DeepSeek API
            log.info("正在调用DeepSeek API进行分类...");
            String aiResponse = callDeepSeekAPI(prompt);
            log.info("DeepSeek API调用成功，响应长度: {}", aiResponse.length());
            log.debug("AI响应内容: {}", aiResponse);
            
            // 4. 解析AI响应并生成分类
            AICategoryResult result = parseAIResponse(aiResponse, results, originalQuery);
            
            if (result != null) {
                log.info("AI分类成功 - 生成 {} 个分类选项", result.getOptions().size());
                for (Option option : result.getOptions()) {
                    log.debug("  - {}", option.getText());
                }
            } else {
                log.warn("AI分类解析失败");
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("AI分类失败，错误信息: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 构建文档摘要（发送给AI的数据）
     */
    private String buildDocumentSummary(List<CircuitDocument> results) {
        StringBuilder summary = new StringBuilder();
        
        // 扩大发送给AI的文档数量，支持更大范围的分类
        // 对于100条结果，发送前50条详细信息
        int limit = Math.min(results.size(), 50);
        
        for (int i = 0; i < limit; i++) {
            CircuitDocument doc = results.get(i);
            // 包含更多信息：ID、层级路径、文件名
            summary.append(String.format("%d. [ID:%d] %s\n   路径: %s\n", 
                    i + 1, 
                    doc.getId(),
                    doc.getFileName() != null ? doc.getFileName() : "未知文件名",
                    doc.getHierarchyPath() != null ? doc.getHierarchyPath() : "未知路径"));
        }
        
        if (results.size() > limit) {
            summary.append(String.format("... 还有 %d 条相似资料\n", results.size() - limit));
        }
        
        // 添加统计信息
        summary.append("\n**资料统计：**\n");
        summary.append(String.format("- 总计：%d 条资料\n", results.size()));
        
        // 统计主要品牌
        Map<String, Integer> brandCount = new HashMap<>();
        for (CircuitDocument doc : results) {
            String text = doc.getFileName() + " " + doc.getHierarchyPath();
            if (text.contains("东风") || text.contains("天龙")) brandCount.merge("东风天龙", 1, Integer::sum);
            if (text.contains("解放")) brandCount.merge("解放", 1, Integer::sum);
            if (text.contains("重汽")) brandCount.merge("重汽", 1, Integer::sum);
            if (text.contains("三一")) brandCount.merge("三一", 1, Integer::sum);
            if (text.contains("徐工")) brandCount.merge("徐工", 1, Integer::sum);
            if (text.contains("卡特")) brandCount.merge("卡特彼勒", 1, Integer::sum);
        }
        
        if (!brandCount.isEmpty()) {
            summary.append("- 主要品牌：");
            brandCount.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(3)
                    .forEach(entry -> summary.append(String.format("%s(%d条) ", entry.getKey(), entry.getValue())));
            summary.append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * 构建AI分类提示词
     */
    private String buildCategorizePrompt(String originalQuery, String documentSummary, int totalCount) {
        return String.format("""
            你是一个专业的汽车电路图资料分类专家。我正在开发一个智能车辆电路图资料导航系统。
            
            **项目背景：**
            - 这是一个包含4000+条汽车电路图资料的数据库
            - 涵盖商用车（东风天龙、解放、重汽等）和工程机械（三一、徐工、卡特等）
            - 资料类型包括：整车电路图、ECU针脚定义、仪表电路图、保险丝盒图等
            - 涉及各种ECU型号：EDC7、EDC17系列、CM2880、DCM3.7等
            
            **用户查询：** "%s"
            
            **搜索到的资料（共%d条）：**
            %s
            
            **任务要求：**
            请分析用户查询意图和搜索结果，生成3-6个专业、精准的分类选项，帮助用户快速定位需要的资料。
            
            **重要说明：**
            - 系统支持多次分类，用户选择一个分类后，如果结果仍然较多，会继续进行二次分类
            - 因此第一次分类可以相对宽泛，但要有明确的区分度
            - 每个分类应该包含足够的文档（建议10-30条），避免分类过细
            
            **分类要求：**
            1. 分类标签要专业且具体，体现汽车电路图领域的专业性
            2. 要考虑用户查询的具体意图（如品牌、型号、部件类型、ECU型号等）
            3. 分类要互斥且有意义，避免过于宽泛或重叠
            4. 每个分类要包含足够的文档数量（至少5-10条）
            5. 分类标签要让用户一眼就明白包含什么内容
            6. 优先按照最能区分资料的维度分类（如发动机型号、车型系列、部件类型等）
            
            **输出格式（严格按此JSON格式）：**
            {
                "prompt": "我找到了多个与[查询内容]相关的电路图资料，请问您需要的是以下哪种类型？",
                "categories": [
                    {
                        "label": "具体的分类标签（如：天龙雷诺DCI发动机ECU电路图）",
                        "description": "详细说明这个分类包含什么",
                        "keywords": ["关键词1", "关键词2", "关键词3"]
                    }
                ]
            }
            
            **参考示例（针对"东风天龙雷诺电路图"查询，共80条结果）：**
            - "天龙雷诺DCI11发动机系列（EDC7、EDC16等ECU）"
            - "天龙雷诺DCI5发动机系列"
            - "天龙雷诺天然气发动机系列"
            - "天龙雷诺整车电路图（含仪表、保险丝等）"
            """, originalQuery, totalCount, documentSummary);
    }
    
    /**
     * 调用DeepSeek API（带重试机制）
     */
    private String callDeepSeekAPI(String prompt) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("DeepSeek API Key未配置");
        }
        
        int maxRetries = 2; // 最多重试2次
        int retryDelay = 2000; // 重试间隔2秒
        
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                log.debug("DeepSeek API调用尝试 {}/{}", attempt, maxRetries + 1);
                return callDeepSeekAPIOnce(prompt);
                
            } catch (Exception e) {
                log.warn("DeepSeek API调用失败 (尝试 {}/{}): {}", attempt, maxRetries + 1, e.getMessage());
                
                if (attempt <= maxRetries) {
                    try {
                        log.info("等待 {}ms 后重试...", retryDelay);
                        Thread.sleep(retryDelay);
                        retryDelay *= 2; // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                } else {
                    // 最后一次尝试失败
                    log.error("DeepSeek API调用最终失败，已尝试 {} 次", attempt);
                    throw new RuntimeException("AI分类服务暂时不可用: " + e.getMessage(), e);
                }
            }
        }
        
        throw new RuntimeException("DeepSeek API调用失败"); // 不应该到达这里
    }
    
    /**
     * 单次调用DeepSeek API
     */
    private String callDeepSeekAPIOnce(String prompt) {
        try {
            log.debug("准备调用DeepSeek API，提示词长度: {}", prompt.length());
            
            // 构建请求体（简单字符串拼接）
            String requestBody = String.format("""
                {
                    "model": "deepseek-chat",
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ],
                    "max_tokens": 2000,
                    "temperature": 0.1
                }
                """, prompt.replace("\"", "\\\"").replace("\n", "\\n"));
            
            log.debug("请求体构建完成，长度: {}", requestBody.length());
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("User-Agent", "ChatBot/1.0");
            
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            
            log.info("发送DeepSeek API请求到: {}", apiUrl);
            
            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
            
            log.info("DeepSeek API响应状态: {}", response.getStatusCode());
            log.debug("DeepSeek API完整响应: {}", response.getBody());
            
            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                if (responseBody == null || responseBody.trim().isEmpty()) {
                    throw new RuntimeException("DeepSeek API返回空响应");
                }
                
                // 检查是否是错误响应
                if (responseBody.contains("error")) {
                    log.error("DeepSeek API返回错误: {}", responseBody);
                    throw new RuntimeException("DeepSeek API错误: " + responseBody);
                }
                
                // 提取content内容
                return extractContentFromResponse(responseBody);
            } else {
                String errorMsg = String.format("DeepSeek API调用失败: %s, 响应: %s", 
                    response.getStatusCode(), response.getBody());
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
        } catch (Exception e) {
            log.error("调用DeepSeek API失败", e);
            throw e; // 重新抛出异常供重试机制处理
        }
    }
    
    /**
     * 从API响应中提取内容
     */
    private String extractContentFromResponse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            throw new RuntimeException("DeepSeek API返回空响应");
        }
        
        log.debug("原始API响应长度: {}", responseBody.length());
        log.debug("原始API响应前500字符: {}", responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
        
        try {
            // 方法1: 使用更强大的正则表达式提取content字段（支持多行JSON）
            Pattern contentPattern = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL);
            Matcher contentMatcher = contentPattern.matcher(responseBody);
            
            if (contentMatcher.find()) {
                String rawContent = contentMatcher.group(1);
                String content = unescapeJsonString(rawContent);
                log.debug("方法1成功提取content，长度: {}", content.length());
                log.debug("提取的content前200字符: {}", content.length() > 200 ? content.substring(0, 200) + "..." : content);
                return content;
            }
            
            // 方法2: 查找choices数组中的message.content
            Pattern choicesPattern = Pattern.compile("\"choices\"\\s*:\\s*\\[\\s*\\{[^}]*\"message\"\\s*:\\s*\\{[^}]*\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL);
            Matcher choicesMatcher = choicesPattern.matcher(responseBody);
            
            if (choicesMatcher.find()) {
                String rawContent = choicesMatcher.group(1);
                String content = unescapeJsonString(rawContent);
                log.debug("方法2成功提取content，长度: {}", content.length());
                log.debug("提取的content前200字符: {}", content.length() > 200 ? content.substring(0, 200) + "..." : content);
                return content;
            }
            
            // 方法3: 手动解析JSON结构
            String content = extractContentManually(responseBody);
            if (content != null) {
                log.debug("方法3成功提取content，长度: {}", content.length());
                return content;
            }
            
            // 方法4: 如果响应本身就是JSON格式的内容（直接包含prompt和categories）
            if (responseBody.contains("\"prompt\"") && responseBody.contains("\"categories\"")) {
                log.debug("方法4：响应本身就是目标JSON内容");
                return responseBody;
            }
            
            log.error("所有方法都无法提取content字段");
            log.error("完整响应内容: {}", responseBody);
            throw new RuntimeException("无法从DeepSeek API响应中提取content字段");
            
        } catch (Exception e) {
            log.error("解析API响应失败", e);
            throw new RuntimeException("解析DeepSeek API响应失败: " + e.getMessage());
        }
    }
    
    /**
     * 手动解析JSON中的content字段
     */
    private String extractContentManually(String responseBody) {
        try {
            // 查找"content"字段的开始位置
            int contentStart = responseBody.indexOf("\"content\"");
            if (contentStart == -1) {
                return null;
            }
            
            // 查找冒号后的引号
            int colonPos = responseBody.indexOf(":", contentStart);
            if (colonPos == -1) {
                return null;
            }
            
            // 跳过空白字符，找到开始引号
            int quoteStart = -1;
            for (int i = colonPos + 1; i < responseBody.length(); i++) {
                char c = responseBody.charAt(i);
                if (c == '"') {
                    quoteStart = i + 1;
                    break;
                } else if (!Character.isWhitespace(c)) {
                    break;
                }
            }
            
            if (quoteStart == -1) {
                return null;
            }
            
            // 查找结束引号（考虑转义）
            StringBuilder content = new StringBuilder();
            boolean escaped = false;
            
            for (int i = quoteStart; i < responseBody.length(); i++) {
                char c = responseBody.charAt(i);
                
                if (escaped) {
                    // 处理转义字符
                    switch (c) {
                        case 'n': content.append('\n'); break;
                        case 't': content.append('\t'); break;
                        case 'r': content.append('\r'); break;
                        case '\\': content.append('\\'); break;
                        case '"': content.append('"'); break;
                        default: content.append(c); break;
                    }
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    // 找到结束引号
                    return content.toString();
                } else {
                    content.append(c);
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("手动解析JSON失败", e);
            return null;
        }
    }
    
    /**
     * 反转义JSON字符串
     */
    private String unescapeJsonString(String escaped) {
        if (escaped == null) {
            return null;
        }
        
        return escaped
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }
    
    /**
     * 解析AI响应并生成分类结果
     */
    private AICategoryResult parseAIResponse(String aiResponse, List<CircuitDocument> results, String originalQuery) {
        try {
            log.debug("开始解析AI响应: {}", aiResponse);
            
            // 提取JSON部分（更智能的提取）
            String jsonStr = extractJSONFromResponse(aiResponse);
            if (jsonStr == null) {
                log.warn("无法从AI响应中提取JSON: {}", aiResponse);
                return null;
            }
            
            log.debug("提取的JSON: {}", jsonStr);
            
            // 解析JSON字段
            String prompt = extractJsonField(jsonStr, "prompt");
            List<CategoryInfo> categories = extractCategories(jsonStr);
            
            if (prompt == null || categories.isEmpty()) {
                log.warn("AI响应格式不正确 - prompt: {}, categories: {}", prompt, categories.size());
                return null;
            }
            
            log.info("解析到 {} 个分类", categories.size());
            
            Map<String, List<CircuitDocument>> categoryMap = new LinkedHashMap<>();
            List<Option> options = new ArrayList<>();
            
            int index = 1;
            for (CategoryInfo category : categories) {
                log.debug("处理分类: {} - 关键词: {}", category.label, category.keywords);
                
                // 根据关键词筛选文档
                List<CircuitDocument> categoryDocs = results.stream()
                    .filter(doc -> matchesAICategory(doc, category.keywords))
                    .collect(Collectors.toList());
                
                log.debug("分类 '{}' 匹配到 {} 个文档", category.label, categoryDocs.size());
                
                if (!categoryDocs.isEmpty()) {
                    categoryMap.put(category.label, categoryDocs);
                    options.add(new Option(index++, category.label, "ai_category:" + category.label));
                }
            }
            
            if (options.size() >= 2) {
                log.info("AI分类成功生成 {} 个有效选项", options.size());
                return new AICategoryResult(prompt, options, categoryMap);
            } else {
                log.warn("有效分类选项不足: {}", options.size());
            }
            
        } catch (Exception e) {
            log.error("解析AI响应失败", e);
        }
        
        return null;
    }
    
    /**
     * 更智能的JSON提取
     */
    private String extractJSONFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        
        // 方法1: 查找完整的JSON对象（改进版，支持嵌套）
        int start = response.indexOf('{');
        if (start >= 0) {
            int braceCount = 0;
            int end = start;
            boolean inString = false;
            boolean escaped = false;
            
            for (int i = start; i < response.length(); i++) {
                char c = response.charAt(i);
                
                if (escaped) {
                    escaped = false;
                    continue;
                }
                
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                
                if (c == '"') {
                    inString = !inString;
                    continue;
                }
                
                if (!inString) {
                    if (c == '{') {
                        braceCount++;
                    } else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            end = i;
                            break;
                        }
                    }
                }
            }
            
            if (end > start && braceCount == 0) {
                String jsonStr = response.substring(start, end + 1);
                log.debug("提取的JSON长度: {}", jsonStr.length());
                log.debug("提取的JSON前200字符: {}", jsonStr.length() > 200 ? jsonStr.substring(0, 200) + "..." : jsonStr);
                return jsonStr;
            }
        }
        
        // 方法2: 如果没找到完整JSON，检查是否包含必要字段
        if (response.contains("\"prompt\"") && response.contains("\"categories\"")) {
            log.debug("响应包含必要字段，直接使用");
            return response;
        }
        
        // 方法3: 尝试修复常见的JSON格式问题
        String cleaned = response.trim();
        if (!cleaned.startsWith("{") && cleaned.contains("{")) {
            // 可能有前缀，尝试从第一个{开始
            int firstBrace = cleaned.indexOf('{');
            cleaned = cleaned.substring(firstBrace);
        }
        
        if (cleaned.contains("\"prompt\"") && cleaned.contains("\"categories\"")) {
            log.debug("清理后的响应包含必要字段");
            return cleaned;
        }
        
        log.warn("无法提取有效的JSON，响应长度: {}", response.length());
        return null;
    }
    
    /**
     * 分类信息类
     */
    private static class CategoryInfo {
        String label;
        String description;
        List<String> keywords;
        
        CategoryInfo(String label, String description, List<String> keywords) {
            this.label = label;
            this.description = description;
            this.keywords = keywords;
        }
    }
    
    /**
     * 提取分类信息（改进版 - 修复嵌套数组问题）
     */
    private List<CategoryInfo> extractCategories(String json) {
        List<CategoryInfo> categories = new ArrayList<>();
        
        try {
            // 查找categories数组的开始位置
            int categoriesStart = json.indexOf("\"categories\"");
            if (categoriesStart == -1) {
                log.warn("未找到categories字段");
                return categories;
            }
            
            // 查找数组开始的 [
            int arrayStart = json.indexOf('[', categoriesStart);
            if (arrayStart == -1) {
                log.warn("未找到categories数组开始符号");
                return categories;
            }
            
            // 手动查找匹配的 ] （考虑嵌套）
            int bracketCount = 0;
            int arrayEnd = arrayStart;
            boolean inString = false;
            boolean escaped = false;
            
            for (int i = arrayStart; i < json.length(); i++) {
                char c = json.charAt(i);
                
                if (escaped) {
                    escaped = false;
                    continue;
                }
                
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                
                if (c == '"') {
                    inString = !inString;
                    continue;
                }
                
                if (!inString) {
                    if (c == '[') {
                        bracketCount++;
                    } else if (c == ']') {
                        bracketCount--;
                        if (bracketCount == 0) {
                            arrayEnd = i;
                            break;
                        }
                    }
                }
            }
            
            if (bracketCount != 0 || arrayEnd == arrayStart) {
                log.warn("未找到匹配的categories数组结束符号");
                return categories;
            }
            
            // 提取categories数组内容（不包括 [ 和 ]）
            String categoriesContent = json.substring(arrayStart + 1, arrayEnd);
            log.debug("找到categories数组内容，长度: {}", categoriesContent.length());
            log.debug("categories内容前300字符: {}", categoriesContent.length() > 300 ? categoriesContent.substring(0, 300) + "..." : categoriesContent);
            
            // 分割每个category对象
            List<String> categoryObjects = extractJsonObjects(categoriesContent);
            log.debug("extractJsonObjects返回 {} 个对象", categoryObjects.size());
            
            for (int i = 0; i < categoryObjects.size(); i++) {
                String categoryContent = categoryObjects.get(i);
                log.debug("处理category对象 {}: {}", i + 1, categoryContent.length() > 100 ? categoryContent.substring(0, 100) + "..." : categoryContent);
                
                String label = extractFieldFromContent(categoryContent, "label");
                String description = extractFieldFromContent(categoryContent, "description");
                List<String> keywords = extractKeywordsFromContent(categoryContent);
                
                log.debug("提取结果 - label: {}, keywords数量: {}", label, keywords.size());
                
                if (label != null && !keywords.isEmpty()) {
                    categories.add(new CategoryInfo(label, description, keywords));
                    log.debug("成功提取分类: {} (关键词数: {})", label, keywords.size());
                } else {
                    log.warn("分类信息不完整 - label: {}, keywords: {}", label, keywords.size());
                }
            }
            
        } catch (Exception e) {
            log.error("提取分类信息失败", e);
        }
        
        log.info("总共提取到 {} 个有效分类", categories.size());
        return categories;
    }
    
    /**
     * 从字符串中提取JSON对象列表（修复版）
     */
    private List<String> extractJsonObjects(String content) {
        List<String> objects = new ArrayList<>();
        
        try {
            log.debug("开始提取JSON对象，输入长度: {}", content.length());
            log.debug("输入内容: {}", content.length() > 300 ? content.substring(0, 300) + "..." : content);
            
            // 方法1: 使用正则表达式直接匹配完整的对象
            Pattern objectPattern = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}", Pattern.DOTALL);
            Matcher objectMatcher = objectPattern.matcher(content);
            
            int regexCount = 0;
            while (objectMatcher.find()) {
                String obj = objectMatcher.group();
                if (obj.contains("\"label\"") && obj.contains("\"keywords\"")) {
                    objects.add(obj);
                    regexCount++;
                    log.debug("正则提取到对象 {}: {}", regexCount, obj.length() > 100 ? obj.substring(0, 100) + "..." : obj);
                }
            }
            
            log.debug("正则表达式匹配到 {} 个有效对象", regexCount);
            
            // 如果方法1没有结果，尝试方法2：手动解析
            if (objects.isEmpty()) {
                log.debug("正则匹配失败，尝试手动解析");
                objects = extractJsonObjectsManually(content);
            }
            
        } catch (Exception e) {
            log.warn("提取JSON对象失败", e);
        }
        
        log.debug("总共提取到 {} 个JSON对象", objects.size());
        return objects;
    }
    
    /**
     * 手动解析JSON对象（备用方法）
     */
    private List<String> extractJsonObjectsManually(String content) {
        List<String> objects = new ArrayList<>();
        
        int start = 0;
        while (start < content.length()) {
            // 查找下一个对象的开始
            int objStart = content.indexOf('{', start);
            if (objStart == -1) {
                break;
            }
            
            // 查找对象的结束
            int braceCount = 0;
            int objEnd = objStart;
            boolean inString = false;
            boolean escaped = false;
            
            for (int i = objStart; i < content.length(); i++) {
                char c = content.charAt(i);
                
                if (escaped) {
                    escaped = false;
                    continue;
                }
                
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                
                if (c == '"') {
                    inString = !inString;
                    continue;
                }
                
                if (!inString) {
                    if (c == '{') {
                        braceCount++;
                    } else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            objEnd = i;
                            break;
                        }
                    }
                }
            }
            
            if (braceCount == 0 && objEnd > objStart) {
                String obj = content.substring(objStart, objEnd + 1);
                // 验证对象是否包含必要字段
                if (obj.contains("\"label\"")) {
                    objects.add(obj);
                    log.debug("手动提取到对象: {}", obj.length() > 100 ? obj.substring(0, 100) + "..." : obj);
                }
                start = objEnd + 1;
            } else {
                // 如果无法找到完整对象，跳过这个开始位置
                start = objStart + 1;
            }
        }
        
        return objects;
    }
    
    /**
     * 从内容中提取字段
     */
    private String extractFieldFromContent(String content, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * 从内容中提取关键词数组（改进版）
     */
    private List<String> extractKeywordsFromContent(String content) {
        List<String> keywords = new ArrayList<>();
        
        try {
            // 方法1: 标准的keywords数组格式
            Pattern keywordsPattern = Pattern.compile("\"keywords\"\\s*:\\s*\\[([^\\]]+)\\]", Pattern.DOTALL);
            Matcher keywordsMatcher = keywordsPattern.matcher(content);
            
            if (keywordsMatcher.find()) {
                String keywordsStr = keywordsMatcher.group(1);
                log.debug("找到keywords数组: {}", keywordsStr);
                
                // 提取数组中的每个关键词（改进版，支持多行）
                Pattern keywordPattern = Pattern.compile("\"([^\"]+)\"");
                Matcher keywordMatcher = keywordPattern.matcher(keywordsStr);
                while (keywordMatcher.find()) {
                    String keyword = keywordMatcher.group(1).trim();
                    if (!keyword.isEmpty()) {
                        keywords.add(keyword.toLowerCase());
                    }
                }
            } else {
                log.debug("未找到keywords数组，尝试其他方法");
                
                // 方法2: 如果没有找到keywords数组，尝试从label中提取关键词
                String label = extractFieldFromContent(content, "label");
                if (label != null) {
                    // 从标签中提取关键词
                    String[] labelWords = label.toLowerCase()
                            .replaceAll("[（）()【】\\[\\]，,、]", " ")
                            .split("\\s+");
                    
                    for (String word : labelWords) {
                        if (word.length() >= 2) {
                            keywords.add(word);
                        }
                    }
                    log.debug("从label提取关键词: {}", keywords);
                }
            }
            
        } catch (Exception e) {
            log.warn("提取关键词失败", e);
        }
        
        log.debug("最终提取到 {} 个关键词: {}", keywords.size(), keywords);
        return keywords;
    }
    
    /**
     * 从JSON字符串中提取字段值
     */
    private String extractJsonField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    
    /**
     * 检查文档是否匹配AI分类（改进版）
     */
    private boolean matchesAICategory(CircuitDocument doc, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        
        String text = buildDocumentText(doc);
        String lowerText = text.toLowerCase();
        
        // 计算匹配分数
        int totalKeywords = keywords.size();
        int matchedKeywords = 0;
        int strongMatches = 0; // 强匹配（完全匹配）
        
        for (String keyword : keywords) {
            if (keyword.length() < 2) continue;
            
            String lowerKeyword = keyword.toLowerCase();
            
            // 完全匹配
            if (lowerText.contains(lowerKeyword)) {
                matchedKeywords++;
                
                // 检查是否是强匹配（独立词汇）
                if (isStrongMatch(lowerText, lowerKeyword)) {
                    strongMatches++;
                }
            }
            // 部分匹配（处理变体）
            else if (hasPartialMatch(lowerText, lowerKeyword)) {
                matchedKeywords++;
            }
        }
        
        // 匹配策略：
        // 1. 如果有强匹配，只需要匹配30%的关键词
        // 2. 如果没有强匹配，需要匹配50%的关键词
        // 3. 至少要匹配1个关键词
        
        double matchRate = (double) matchedKeywords / totalKeywords;
        
        if (strongMatches > 0) {
            return matchRate >= 0.3 && matchedKeywords >= 1;
        } else {
            return matchRate >= 0.5 && matchedKeywords >= Math.min(2, totalKeywords);
        }
    }
    
    /**
     * 构建文档的完整文本
     */
    private String buildDocumentText(CircuitDocument doc) {
        StringBuilder text = new StringBuilder();
        
        if (doc.getHierarchyPath() != null) {
            text.append(doc.getHierarchyPath()).append(" ");
        }
        if (doc.getFileName() != null) {
            text.append(doc.getFileName()).append(" ");
        }
        
        return text.toString();
    }
    
    /**
     * 检查是否是强匹配（独立词汇）
     */
    private boolean isStrongMatch(String text, String keyword) {
        // 检查关键词是否作为独立词汇出现
        String pattern = "\\b" + Pattern.quote(keyword) + "\\b";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
    
    /**
     * 检查是否有部分匹配
     */
    private boolean hasPartialMatch(String text, String keyword) {
        // 处理常见变体
        Map<String, String[]> variants = new HashMap<>();
        variants.put("天龙", new String[]{"tianlong", "tl"});
        variants.put("雷诺", new String[]{"renault", "雷诺"});
        variants.put("dci", new String[]{"DCI", "dci发动机"});
        variants.put("ecu", new String[]{"ECU", "电脑板", "控制器"});
        variants.put("整车", new String[]{"全车", "车辆"});
        
        if (variants.containsKey(keyword)) {
            for (String variant : variants.get(keyword)) {
                if (text.contains(variant.toLowerCase())) {
                    return true;
                }
            }
        }
        
        // 检查包含关系（关键词的一部分）
        if (keyword.length() >= 4) {
            for (int i = 0; i <= keyword.length() - 3; i++) {
                String subKeyword = keyword.substring(i, i + 3);
                if (text.contains(subKeyword)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 根据AI分类筛选结果（第二步AI处理）
     */
    public List<CircuitDocument> filterByAICategory(List<CircuitDocument> results, 
                                                   String categoryLabel, 
                                                   Map<String, List<CircuitDocument>> categoryMap) {
        // 如果有预计算的分类映射，直接使用
        if (categoryMap != null && categoryMap.containsKey(categoryLabel)) {
            return categoryMap.get(categoryLabel);
        }
        
        // 否则调用AI进行精确筛选
        return filterWithAI(results, categoryLabel);
    }
    
    /**
     * 使用AI进行精确筛选
     */
    private List<CircuitDocument> filterWithAI(List<CircuitDocument> results, String categoryLabel) {
        try {
            // 构建筛选提示词
            String filterPrompt = buildFilterPrompt(results, categoryLabel);
            
            // 调用AI
            String aiResponse = callDeepSeekAPI(filterPrompt);
            
            // 解析AI返回的文档ID列表
            List<Integer> selectedIds = parseFilterResponse(aiResponse);
            
            // 根据ID筛选文档
            return results.stream()
                    .filter(doc -> selectedIds.contains(doc.getId()))
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("AI筛选失败，使用关键词匹配降级", e);
            // 降级到关键词匹配
            return filterByKeywords(results, categoryLabel);
        }
    }
    
    /**
     * 构建AI筛选提示词
     */
    private String buildFilterPrompt(List<CircuitDocument> results, String categoryLabel) {
        StringBuilder documentList = new StringBuilder();
        for (CircuitDocument doc : results) {
            documentList.append(String.format("ID:%d - %s\n", doc.getId(), doc.getFileName()));
        }
        
        return String.format("""
            你是汽车电路图资料筛选专家。用户选择了分类："%s"
            
            **任务：** 从以下资料中筛选出符合该分类的文档
            
            **资料列表：**
            %s
            
            **筛选要求：**
            1. 严格按照分类标准筛选
            2. 只选择真正符合该分类的资料
            3. 宁可少选，不要误选
            4. 考虑汽车电路图领域的专业知识
            
            **输出格式：**
            请返回符合条件的文档ID列表，用逗号分隔，如：1,5,8,12
            
            如果没有符合的文档，返回：无
            """, categoryLabel, documentList.toString());
    }
    
    /**
     * 解析AI筛选响应
     */
    private List<Integer> parseFilterResponse(String aiResponse) {
        List<Integer> ids = new ArrayList<>();
        
        if (aiResponse.contains("无") || aiResponse.contains("没有")) {
            return ids;
        }
        
        // 提取数字ID
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(aiResponse);
        while (matcher.find()) {
            try {
                ids.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException e) {
                // 忽略无效数字
            }
        }
        
        return ids;
    }
    
    /**
     * 关键词匹配降级方案
     */
    private List<CircuitDocument> filterByKeywords(List<CircuitDocument> results, String categoryLabel) {
        // 从分类标签中提取关键词
        String[] keywords = categoryLabel.toLowerCase()
                .replaceAll("[（）()【】\\[\\]]", " ")
                .split("[\\s,，、]+");
        
        return results.stream()
                .filter(doc -> {
                    String text = (doc.getFileName() != null ? doc.getFileName() : "") + 
                                 (doc.getHierarchyPath() != null ? doc.getHierarchyPath() : "");
                    String lowerText = text.toLowerCase();
                    
                    // 至少匹配一半的关键词
                    int matchCount = 0;
                    for (String keyword : keywords) {
                        if (keyword.length() >= 2 && lowerText.contains(keyword)) {
                            matchCount++;
                        }
                    }
                    return matchCount >= Math.max(1, keywords.length / 2);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 测试DeepSeek API连接
     */
    public String testAPIConnection() {
        try {
            String testPrompt = "请简单回复：API连接正常";
            String response = callDeepSeekAPI(testPrompt);
            return "API连接成功，响应: " + response;
        } catch (Exception e) {
            return "API连接失败: " + e.getMessage();
        }
    }
}