package com.bo.chatbot.service;

import com.bo.chatbot.model.QueryInfo;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 查询理解服务
 * 使用 DeepSeek AI 理解用户的自然语言查询
 */
@Slf4j
@Service
public class QueryUnderstandingService {
    
    @Autowired
    private DeepSeekService deepSeekService;
    
    @Autowired
    private PromptBuilder promptBuilder;
    
    private final Gson gson = new Gson();
    
    /**
     * 理解用户查询
     * 
     * @param userQuery 用户查询文本
     * @return QueryInfo 对象
     */
    public QueryInfo understand(String userQuery) {
        try {
            log.info("开始理解用户查询: {}", userQuery);
            
            // 1. 构建 Prompt
            String prompt = promptBuilder.buildQueryUnderstandingPrompt(userQuery);
            
            // 2. 调用 DeepSeek API
            String response = deepSeekService.callDeepSeek(prompt, 0.3, 500);
            
            log.debug("DeepSeek 原始响应: {}", response);
            
            // 3. 解析 JSON 响应
            QueryInfo queryInfo = parseResponse(response);
            
            // 4. 设置原始查询
            queryInfo.setOriginalQuery(userQuery);
            
            log.info("查询理解结果: {}", queryInfo);
            
            return queryInfo;
            
        } catch (Exception e) {
            log.error("理解用户查询失败: {}", userQuery, e);
            // 返回一个空的 QueryInfo，包含原始查询
            QueryInfo fallback = new QueryInfo();
            fallback.setOriginalQuery(userQuery);
            return fallback;
        }
    }
    
    /**
     * 解析 AI 返回的 JSON 响应
     * 
     * @param response AI 返回的文本
     * @return QueryInfo 对象
     */
    private QueryInfo parseResponse(String response) {
        try {
            // 清理响应文本（去除可能的 markdown 代码块标记）
            String cleanedResponse = response.trim();
            
            // 去除 ```json 和 ``` 标记
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.substring(7);
            } else if (cleanedResponse.startsWith("```")) {
                cleanedResponse = cleanedResponse.substring(3);
            }
            
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            
            cleanedResponse = cleanedResponse.trim();
            
            // 解析 JSON
            QueryInfo queryInfo = gson.fromJson(cleanedResponse, QueryInfo.class);
            
            // 标准化数据
            normalizeQueryInfo(queryInfo);
            
            return queryInfo;
            
        } catch (JsonSyntaxException e) {
            log.error("解析 JSON 失败，响应内容: {}", response, e);
            
            // 尝试手动提取 JSON
            QueryInfo queryInfo = extractJsonManually(response);
            if (queryInfo != null) {
                return queryInfo;
            }
            
            // 如果都失败了，返回空对象
            return new QueryInfo();
        }
    }
    
    /**
     * 标准化 QueryInfo 数据
     * 处理 null 字符串、空字符串等
     */
    private void normalizeQueryInfo(QueryInfo queryInfo) {
        if (queryInfo == null) {
            return;
        }
        
        // 将 "null" 字符串转为 null
        if ("null".equalsIgnoreCase(queryInfo.getBrand())) {
            queryInfo.setBrand(null);
        }
        if ("null".equalsIgnoreCase(queryInfo.getModel())) {
            queryInfo.setModel(null);
        }
        if ("null".equalsIgnoreCase(queryInfo.getComponent())) {
            queryInfo.setComponent(null);
        }
        if ("null".equalsIgnoreCase(queryInfo.getEcuType())) {
            queryInfo.setEcuType(null);
        }
        if ("null".equalsIgnoreCase(queryInfo.getQueryType())) {
            queryInfo.setQueryType(null);
        }
        
        // 去除空白字符
        if (queryInfo.getBrand() != null) {
            queryInfo.setBrand(queryInfo.getBrand().trim());
        }
        if (queryInfo.getModel() != null) {
            queryInfo.setModel(queryInfo.getModel().trim());
        }
        if (queryInfo.getComponent() != null) {
            queryInfo.setComponent(queryInfo.getComponent().trim());
        }
        if (queryInfo.getEcuType() != null) {
            queryInfo.setEcuType(queryInfo.getEcuType().trim());
        }
    }
    
    /**
     * 手动提取 JSON（备用方案）
     * 当 Gson 解析失败时使用
     */
    private QueryInfo extractJsonManually(String response) {
        try {
            // 查找 JSON 对象的开始和结束位置
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            
            if (start >= 0 && end > start) {
                String jsonStr = response.substring(start, end + 1);
                return gson.fromJson(jsonStr, QueryInfo.class);
            }
        } catch (Exception e) {
            log.warn("手动提取 JSON 也失败了", e);
        }
        
        return null;
    }
    
    /**
     * 测试查询理解功能
     */
    public void test() {
        String[] testQueries = {
            "红岩杰狮保险丝图纸",
            "康明斯2880电路图",
            "小忪2ooo供电电路图",
            "三一挖掘机仪表"
        };
        
        log.info("========== 开始测试查询理解 ==========");
        for (String query : testQueries) {
            QueryInfo result = understand(query);
            log.info("查询: {} -> 结果: {}", query, result);
        }
        log.info("========== 测试完成 ==========");
    }
}
