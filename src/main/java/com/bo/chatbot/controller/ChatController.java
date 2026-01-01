package com.bo.chatbot.controller;

import com.bo.chatbot.model.*;
import com.bo.chatbot.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天 API 控制器
 * 处理前端的聊天请求，支持多轮对话引导
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class ChatController {
    
    /**
     * 最大返回结果数
     */
    private static final int MAX_RESULTS = 5;
    
    @Autowired
    private DataLoaderService dataLoaderService;
    
    @Autowired
    private QueryUnderstandingService queryUnderstandingService;
    
    @Autowired
    private SmartSearchEngine smartSearchEngine;
    
    @Autowired
    private ResultCategorizer resultCategorizer;
    
    @Autowired
    private ConversationManager conversationManager;
    
    /**
     * 发送消息接口
     * POST /api/chat
     */
    @PostMapping("/chat")
    public Result<ChatResponseData> chat(@RequestBody ChatRequest request) {
        log.info("收到聊天请求 - SessionId: {}, Message: {}", 
                request.getSessionId(), request.getMessage());
        
        try {
            // 验证参数
            if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
                return Result.error("会话ID不能为空");
            }
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return Result.error("消息内容不能为空");
            }
            
            String sessionId = request.getSessionId();
            String message = request.getMessage().trim();
            
            // 检查是否是问候或闲聊
            if (isGreetingOrChat(message)) {
                return Result.success(buildWelcomeResponse());
            }
            
            // 使用 AI 理解用户查询
            QueryInfo queryInfo = null;
            try {
                queryInfo = queryUnderstandingService.understand(message);
                log.info("AI 理解结果: {}", queryInfo);
            } catch (Exception e) {
                log.warn("AI 理解失败，降级到关键词搜索", e);
            }
            
            // 检查是否是无效查询
            if (queryInfo == null || !queryInfo.hasValidInfo()) {
                // 尝试关键词搜索
                List<CircuitDocument> results = smartSearchEngine.searchByKeyword(message);
                if (results.isEmpty()) {
                    return Result.success(buildNoResultResponse());
                }
                return processSearchResults(sessionId, results, null, results.size());
            }
            
            // 保存原始查询
            queryInfo.setOriginalQuery(message);
            
            // 执行智能搜索
            List<CircuitDocument> results = smartSearchEngine.search(queryInfo);
            log.info("智能搜索 - QueryInfo: {}, 找到 {} 条结果", queryInfo, results.size());
            
            // 保存到会话
            conversationManager.saveSearchResults(sessionId, queryInfo, results, null);
            
            // 处理搜索结果
            return processSearchResults(sessionId, results, queryInfo, results.size());
            
        } catch (Exception e) {
            log.error("处理聊天请求失败", e);
            return Result.error("系统繁忙，请稍后重试");
        }
    }

    /**
     * 处理搜索结果，根据数量决定返回方式
     */
    private Result<ChatResponseData> processSearchResults(String sessionId, 
            List<CircuitDocument> results, QueryInfo queryInfo, int totalCount) {
        
        if (results.isEmpty()) {
            return Result.success(buildNoResultResponse());
        }
        
        if (results.size() == 1) {
            // 唯一结果，直接返回
            return Result.success(buildSingleResultResponse(results.get(0)));
        }
        
        if (results.size() <= MAX_RESULTS) {
            // 结果数量合适，返回选择列表
            return Result.success(buildOptionsResponse(results, totalCount));
        }
        
        // 结果较多，尝试分类引导
        ResultCategorizer.CategoryResult category = resultCategorizer.categorize(results, totalCount);
        
        if (category != null && category.getOptions().size() >= 2) {
            // 可以分类，返回分类选项
            log.info("生成分类选项 - 类型: {}, 选项数: {}", 
                    category.getCategoryType(), category.getOptions().size());
            
            // 保存分类类型到会话
            conversationManager.getOrCreateSession(sessionId)
                    .setLastCategoryType(category.getCategoryType());
            
            ChatResponseData data = ChatResponseData.options(
                    category.getPrompt(),
                    category.getOptions()
            );
            return Result.success(data);
        }
        
        // 无法分类，返回前5个结果
        List<CircuitDocument> topResults = results.stream()
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
        
        return Result.success(buildOptionsResponse(topResults, totalCount));
    }
    
    /**
     * 处理用户选择接口
     * POST /api/select
     */
    @PostMapping("/select")
    public Result<ChatResponseData> select(@RequestBody SelectRequest request) {
        log.info("收到选择请求 - SessionId: {}, OptionId: {}, OptionValue: {}", 
                request.getSessionId(), request.getOptionId(), request.getOptionValue());
        
        try {
            String sessionId = request.getSessionId();
            String optionValue = request.getOptionValue();
            
            if (optionValue == null || optionValue.trim().isEmpty()) {
                return Result.error("选项值不能为空");
            }
            
            // 检查是否是分类选择
            if (conversationManager.isCategorySelection(optionValue)) {
                return handleCategorySelection(sessionId, optionValue);
            }
            
            // 普通文档选择
            return handleDocumentSelection(optionValue);
            
        } catch (Exception e) {
            log.error("处理选择请求失败", e);
            return Result.error("系统繁忙，请稍后重试");
        }
    }
    
    /**
     * 处理分类选择
     */
    private Result<ChatResponseData> handleCategorySelection(String sessionId, String optionValue) {
        String categoryValue = conversationManager.parseCategoryValue(optionValue);
        
        // 获取上次搜索结果
        List<CircuitDocument> lastResults = conversationManager.getLastResults(sessionId);
        if (lastResults == null || lastResults.isEmpty()) {
            return Result.error("会话已过期，请重新搜索");
        }
        
        // 获取分类类型
        ConversationManager.ConversationState state = conversationManager.getSession(sessionId);
        String categoryType = state != null ? state.getLastCategoryType() : "model";
        
        // 筛选结果
        List<CircuitDocument> filtered = resultCategorizer.filterByCategory(
                lastResults, categoryType, categoryValue);
        
        log.info("分类筛选 - 类型: {}, 值: {}, 筛选前: {}, 筛选后: {}", 
                categoryType, categoryValue, lastResults.size(), filtered.size());
        
        // 更新会话
        conversationManager.updateFilteredResults(sessionId, filtered);
        
        // 增加确认步骤计数
        state.setNarrowingStep(state.getNarrowingStep() + 1);
        
        // 继续处理筛选后的结果（带确认语）
        return processFilteredResults(sessionId, filtered, categoryValue);
    }
    
    /**
     * 处理筛选后的结果（带确认语）
     */
    private Result<ChatResponseData> processFilteredResults(String sessionId, 
            List<CircuitDocument> results, String selectedCategory) {
        
        if (results.isEmpty()) {
            return Result.success(ChatResponseData.text(
                "抱歉，该分类下没有找到匹配的资料。\n请尝试其他选项或重新搜索。"));
        }
        
        // 构建确认语
        String confirmText = String.format("好的，已选择「%s」。", selectedCategory);
        
        if (results.size() == 1) {
            // 唯一结果，直接返回
            String content = String.format("%s\n\n✅ 已为您找到匹配的资料：\n\n[ID: %d] %s", 
                    confirmText, results.get(0).getId(), results.get(0).getFileName());
            return Result.success(ChatResponseData.result(content, results.get(0)));
        }
        
        if (results.size() <= MAX_RESULTS) {
            // 结果数量合适，返回选择列表
            return Result.success(buildOptionsResponseWithConfirm(results, results.size(), confirmText));
        }
        
        // 结果仍然较多，继续分类引导
        ResultCategorizer.CategoryResult category = resultCategorizer.categorize(results, results.size());
        
        if (category != null && category.getOptions().size() >= 2) {
            // 可以继续分类
            conversationManager.getOrCreateSession(sessionId)
                    .setLastCategoryType(category.getCategoryType());
            
            String prompt = confirmText + "\n\n" + category.getPrompt();
            ChatResponseData data = ChatResponseData.options(prompt, category.getOptions());
            return Result.success(data);
        }
        
        // 无法继续分类，返回前5个结果
        List<CircuitDocument> topResults = results.stream()
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
        
        return Result.success(buildOptionsResponseWithConfirm(topResults, results.size(), confirmText));
    }
    
    /**
     * 构建带确认语的选项列表响应
     */
    private ChatResponseData buildOptionsResponseWithConfirm(List<CircuitDocument> results, 
            int totalCount, String confirmText) {
        List<Option> options = buildDocumentOptions(results);
        
        String prompt;
        if (totalCount > results.size()) {
            prompt = String.format("%s\n\n找到 %d 条相关资料，以下是最匹配的 %d 条：", 
                    confirmText, totalCount, results.size());
        } else {
            prompt = String.format("%s\n\n找到以下 %d 条相关资料：", confirmText, results.size());
        }
        
        return ChatResponseData.options(prompt, options);
    }
    
    /**
     * 处理文档选择
     */
    private Result<ChatResponseData> handleDocumentSelection(String optionValue) {
        try {
            Integer docId = Integer.parseInt(optionValue);
            CircuitDocument doc = dataLoaderService.getById(docId);
            
            if (doc == null) {
                return Result.error("未找到对应的文档");
            }
            
            return Result.success(buildSingleResultResponse(doc));
            
        } catch (NumberFormatException e) {
            log.error("解析文档ID失败: {}", optionValue);
            return Result.error("无效的文档ID");
        }
    }
    
    /**
     * 构建欢迎响应
     */
    private ChatResponseData buildWelcomeResponse() {
        return ChatResponseData.text(
            "您好！我是电路图资料助手 🚗\n\n" +
            "我可以帮您查找车辆电路图资料，请输入您要查找的内容，例如：\n" +
            "• \"红岩杰狮保险丝\"\n" +
            "• \"三一挖掘机仪表\"\n" +
            "• \"康明斯2880电路图\"\n\n" +
            "请问您需要查找什么资料？"
        );
    }
    
    /**
     * 构建无结果响应
     */
    private ChatResponseData buildNoResultResponse() {
        return ChatResponseData.text(
            "抱歉，未找到相关资料。\n\n建议您：\n" +
            "1. 检查品牌或型号是否正确\n" +
            "2. 尝试使用更通用的关键词\n" +
            "3. 换一种表达方式\n\n" +
            "例如：\"三一挖掘机\"、\"红岩保险丝\"、\"康明斯ECU\""
        );
    }
    
    /**
     * 构建单个结果响应
     * 显示格式：[ID: xxx] 文档标题
     */
    private ChatResponseData buildSingleResultResponse(CircuitDocument doc) {
        String content = String.format("✅ 已为您找到匹配的资料：\n\n[ID: %d] %s", 
                doc.getId(), doc.getFileName());
        return ChatResponseData.result(content, doc);
    }
    
    /**
     * 构建选项列表响应
     * 格式：A. [ID: xxx] 文档标题
     */
    private ChatResponseData buildOptionsResponse(List<CircuitDocument> results, int totalCount) {
        List<Option> options = buildDocumentOptions(results);
        
        String prompt;
        if (totalCount > results.size()) {
            prompt = String.format("我找到了 %d 条相关资料，以下是最匹配的 %d 条，请选择您需要的：", 
                    totalCount, results.size());
        } else {
            prompt = String.format("我找到了以下 %d 条相关资料，请选择您需要的：", results.size());
        }
        
        return ChatResponseData.options(prompt, options);
    }
    
    /**
     * 构建文档选项列表
     */
    private List<Option> buildDocumentOptions(List<CircuitDocument> results) {
        List<Option> options = new ArrayList<>();
        
        for (int i = 0; i < results.size() && i < 5; i++) {
            CircuitDocument doc = results.get(i);
            // 格式：[ID: xxx] 文档标题
            String displayText = String.format("[ID: %d] %s", doc.getId(), doc.getFileName());
            options.add(new Option(
                i + 1,
                displayText,
                doc.getId().toString()
            ));
        }
        
        return options;
    }

    /**
     * 判断是否是问候或闲聊消息
     */
    private boolean isGreetingOrChat(String message) {
        String[] greetings = {
            "你好", "您好", "hi", "hello", "嗨", "在吗", "在不在",
            "你是谁", "你是什么", "介绍一下", "帮帮我", "帮我", "谢谢",
            "早上好", "下午好", "晚上好", "早安", "晚安"
        };
        
        String lowerMessage = message.toLowerCase().trim();
        for (String greeting : greetings) {
            if (lowerMessage.contains(greeting)) {
                return true;
            }
        }
        
        // 如果消息很短且不包含电路图相关词汇，也认为是闲聊
        if (message.length() <= 5) {
            String[] keywords = {"电路", "图", "保险", "仪表", "ECU", "线路"};
            for (String keyword : keywords) {
                if (message.contains(keyword)) {
                    return false;
                }
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取统计信息接口
     */
    @GetMapping("/stats")
    public Result<Object> getStats() {
        try {
            java.util.Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("totalDocuments", dataLoaderService.getDocumentCount());
            stats.put("status", "运行中");
            stats.putAll(conversationManager.getStats());
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return Result.error("获取统计信息失败");
        }
    }
    
    /**
     * 根据ID查询文档接口
     */
    @GetMapping("/document/{id}")
    public Result<CircuitDocument> getDocument(@PathVariable Integer id) {
        try {
            CircuitDocument doc = dataLoaderService.getById(id);
            if (doc == null) {
                return Result.error("未找到对应的文档");
            }
            return Result.success(doc);
        } catch (Exception e) {
            log.error("查询文档失败", e);
            return Result.error("查询文档失败");
        }
    }
    
    /**
     * 测试 AI 理解接口
     */
    @PostMapping("/test/understand")
    public Result<QueryInfo> testUnderstand(@RequestBody ChatRequest request) {
        try {
            QueryInfo queryInfo = queryUnderstandingService.understand(request.getMessage());
            return Result.success(queryInfo);
        } catch (Exception e) {
            log.error("测试 AI 理解失败", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }
}
