package com.bo.chatbot.controller;

import com.bo.chatbot.model.*;
import com.bo.chatbot.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
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
     * 处理搜索结果，严格按数量分流
     * 1条：直接返回结果
     * 2-5条：显示选择列表
     * >5条：必须进行分类引导，如果无法分类则显示分页结果
     */
    private Result<ChatResponseData> processSearchResults(String sessionId, 
            List<CircuitDocument> results, QueryInfo queryInfo, int totalCount) {
        
        if (results.isEmpty()) {
            return Result.success(buildNoResultResponse());
        }
        
        if (results.size() == 1) {
            // 1条：直接返回结果
            log.info("找到唯一结果，直接返回 - ID: {}", results.get(0).getId());
            return Result.success(buildSingleResultResponse(results.get(0)));
        }
        
        if (results.size() <= MAX_RESULTS) {
            // 2-5条：显示选择列表
            log.info("找到 {} 条结果，显示选择列表", results.size());
            return Result.success(buildOptionsResponse(results, results.size()));
        }
        
        // >5条：必须进行分类引导
        log.info("找到 {} 条结果，尝试分类引导", results.size());
        
        // 获取会话状态，传递已使用的分类类型
        ConversationManager.ConversationState state = conversationManager.getOrCreateSession(sessionId);
        ResultCategorizer.CategoryResult category = resultCategorizer.categorize(
                results, totalCount, state.getUsedCategoryTypes());
        
        if (category != null && category.getOptions().size() >= 2) {
            // 可以分类，返回分类选项
            log.info("生成分类选项 - 类型: {}, 选项数: {}", 
                    category.getCategoryType(), category.getOptions().size());
            
            // 保存分类类型到会话
            state.setLastCategoryType(category.getCategoryType());
            
            ChatResponseData data = ChatResponseData.options(
                    category.getPrompt(),
                    category.getOptions()
            );
            return Result.success(data);
        }
        
        // 无法分类，使用分页显示结果
        log.warn("无法分类 {} 条结果，使用分页显示", results.size());
        return buildPaginatedResponse(sessionId, results, totalCount, 0);
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
            
            // 检查是否是分页请求
            if ("next_page".equals(optionValue)) {
                return handleNextPageRequest(sessionId);
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
        
        // 获取会话状态
        ConversationManager.ConversationState state = conversationManager.getSession(sessionId);
        
        List<CircuitDocument> filtered = null;
        String actualCategoryType = null;
        
        // 优先在当前结果中筛选
        if (state != null) {
            // 首先尝试使用当前分类类型（最准确）
            String currentCategoryType = state.getLastCategoryType();
            if (currentCategoryType != null) {
                List<CircuitDocument> tryFiltered = resultCategorizer.filterByCategory(
                        lastResults, currentCategoryType, categoryValue);
                
                if (!tryFiltered.isEmpty()) {
                    filtered = tryFiltered;
                    actualCategoryType = currentCategoryType;
                    log.info("当前结果筛选成功 - 类型: {}, 值: {}, 筛选前: {}, 筛选后: {}", 
                            currentCategoryType, categoryValue, lastResults.size(), filtered.size());
                }
            }
            
            // 如果当前分类类型没有结果，再尝试其他类型
            if (filtered == null || filtered.isEmpty()) {
                // 动态判断分类类型：尝试所有可能的分类类型
                String[] possibleTypes = {"brand", "model", "component", "ecu"};
                
                for (String tryType : possibleTypes) {
                    // 跳过已经尝试过的当前分类类型
                    if (tryType.equals(currentCategoryType)) {
                        continue;
                    }
                    
                    List<CircuitDocument> tryFiltered = resultCategorizer.filterByCategory(
                            lastResults, tryType, categoryValue);
                    
                    if (!tryFiltered.isEmpty()) {
                        filtered = tryFiltered;
                        actualCategoryType = tryType;
                        log.info("备用类型筛选成功 - 类型: {}, 值: {}, 筛选前: {}, 筛选后: {}", 
                                tryType, categoryValue, lastResults.size(), filtered.size());
                        break;
                    }
                }
            }
            
            // 如果当前结果中没有找到，尝试智能回退
            if ((filtered == null || filtered.isEmpty()) && state.getNarrowingStep() > 0) {
                log.info("当前结果中未找到匹配项，尝试智能回退 - 值: {}, 当前步骤: {}", 
                        categoryValue, state.getNarrowingStep());
                
                // 尝试在原始搜索结果中查找该选项
                QueryInfo originalQuery = conversationManager.getLastQuery(sessionId);
                if (originalQuery != null) {
                    // 重新执行搜索获取原始结果
                    List<CircuitDocument> originalResults = smartSearchEngine.search(originalQuery);
                    
                    // 在原始结果中尝试筛选
                    String[] allPossibleTypes = {"brand", "model", "component", "ecu"};
                    for (String tryType : allPossibleTypes) {
                        List<CircuitDocument> tryFiltered = resultCategorizer.filterByCategory(
                                originalResults, tryType, categoryValue);
                        
                        if (!tryFiltered.isEmpty()) {
                            filtered = tryFiltered;
                            actualCategoryType = tryType;
                            log.info("原始结果筛选成功 - 类型: {}, 值: {}, 筛选前: {}, 筛选后: {}", 
                                    tryType, categoryValue, originalResults.size(), filtered.size());
                            
                            // 重置会话状态到初始状态
                            state.setLastResults(originalResults);
                            state.setNarrowingStep(0);
                            state.resetUsedCategoryTypes();
                            break;
                        }
                    }
                }
            }
        }
        
        // 如果仍然没有结果，给出友好提示
        if (filtered == null || filtered.isEmpty()) {
            String friendlyMessage = String.format(
                "抱歉，没有找到「%s」相关的资料。\n\n" +
                "💡 这可能是因为您选择了较早步骤的选项。建议您：\n" +
                "• 重新开始搜索，使用更具体的关键词\n" +
                "• 或者尝试其他相关的搜索词\n" +
                "• 例如：\"东风天龙仪表\"、\"天龙ECU针脚\"等", 
                categoryValue
            );
            
            return Result.success(ChatResponseData.text(friendlyMessage));
        }
        
        // 记录已使用的分类类型
        if (state != null && actualCategoryType != null) {
            state.addUsedCategoryType(actualCategoryType);
        }
        
        // 检查筛选是否有效果
        if (filtered.size() == lastResults.size()) {
            log.warn("分类筛选无效果，筛选前后数量相同: {}", filtered.size());
            // 强制标记该分类类型已使用，避免重复
            if (state != null && actualCategoryType != null) {
                state.addUsedCategoryType(actualCategoryType);
            }
        }
        
        // 更新会话
        conversationManager.updateFilteredResults(sessionId, filtered);
        
        // 继续处理筛选后的结果（带确认语）
        return processFilteredResults(sessionId, filtered, categoryValue);
    }
    
    /**
     * 处理筛选后的结果（带确认语），严格按数量分流
     */
    private Result<ChatResponseData> processFilteredResults(String sessionId, 
            List<CircuitDocument> results, String selectedCategory) {
        
        if (results.isEmpty()) {
            return Result.success(ChatResponseData.text(
                "抱歉，该分类下没有找到匹配的资料。\n请尝试其他选项或重新搜索。"));
        }
        
        // 构建确认语
        String confirmText = String.format("好的，已选择「%s」。", selectedCategory);
        
        // 增加确认步骤计数
        ConversationManager.ConversationState state = conversationManager.getSession(sessionId);
        if (state != null) {
            state.setNarrowingStep(state.getNarrowingStep() + 1);
        }
        
        if (results.size() == 1) {
            // 1条：直接返回结果
            String content = String.format("%s\n\n✅ 已为您找到匹配的资料：\n\n[ID: %d] %s", 
                    confirmText, results.get(0).getId(), results.get(0).getFileName());
            return Result.success(ChatResponseData.result(content, results.get(0)));
        }
        
        if (results.size() <= MAX_RESULTS) {
            // 2-5条：显示选择列表
            return Result.success(buildOptionsResponseWithConfirm(results, results.size(), confirmText));
        }
        
        // >5条：必须继续分类引导
        ResultCategorizer.CategoryResult category = resultCategorizer.categorize(
                results, results.size(), state != null ? state.getUsedCategoryTypes() : new HashSet<>());
        
        if (category != null && category.getOptions().size() >= 2) {
            // 可以继续分类
            if (state != null) {
                state.setLastCategoryType(category.getCategoryType());
            }
            
            String prompt = confirmText + "\n\n" + category.getPrompt();
            ChatResponseData data = ChatResponseData.options(prompt, category.getOptions());
            return Result.success(data);
        }
        
        // 无法继续分类，使用分页显示结果
        log.warn("筛选后仍有 {} 条结果且无法继续分类，使用分页显示", results.size());
        
        // 更新会话的完整结果用于分页
        if (state != null) {
            state.setAllResults(new ArrayList<>(results));
            state.setCurrentPage(0);
        }
        
        return buildPaginatedResponseWithConfirm(sessionId, results, confirmText);
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
     * 处理下一页请求
     */
    private Result<ChatResponseData> handleNextPageRequest(String sessionId) {
        List<CircuitDocument> nextPageResults = conversationManager.getNextPageResults(sessionId);
        
        if (nextPageResults == null) {
            return Result.error("会话已过期，请重新搜索");
        }
        
        if (nextPageResults.isEmpty()) {
            return Result.success(ChatResponseData.text("已经是最后一页了。"));
        }
        
        ConversationManager.PageInfo pageInfo = conversationManager.getPageInfo(sessionId);
        if (pageInfo == null) {
            return Result.error("分页信息获取失败");
        }
        
        log.info("显示第 {} 页结果，共 {} 条", pageInfo.getCurrentPage(), nextPageResults.size());
        
        return Result.success(buildPaginatedOptionsResponse(nextPageResults, pageInfo));
    }
    
    /**
     * 构建带确认语的分页响应
     */
    private Result<ChatResponseData> buildPaginatedResponseWithConfirm(String sessionId, 
            List<CircuitDocument> allResults, String confirmText) {
        
        // 获取第一页结果
        List<CircuitDocument> pageResults = conversationManager.getPageResults(sessionId, 0);
        if (pageResults == null || pageResults.isEmpty()) {
            return Result.success(ChatResponseData.text("抱歉，没有找到结果。"));
        }
        
        ConversationManager.PageInfo pageInfo = conversationManager.getPageInfo(sessionId);
        if (pageInfo == null) {
            return Result.error("分页信息获取失败");
        }
        
        return Result.success(buildPaginatedOptionsResponseWithConfirm(pageResults, pageInfo, confirmText));
    }
    
    /**
     * 构建带确认语的分页选项响应
     */
    private ChatResponseData buildPaginatedOptionsResponseWithConfirm(List<CircuitDocument> results, 
            ConversationManager.PageInfo pageInfo, String confirmText) {
        
        List<Option> options = buildDocumentOptions(results);
        
        // 如果有下一页，添加"下一页"选项
        if (pageInfo.hasNextPage()) {
            options.add(new Option(
                options.size() + 1,
                String.format("📄 查看下一页（第%d页，共%d页）", 
                        pageInfo.getCurrentPage() + 1, pageInfo.getTotalPages()),
                "next_page"
            ));
        }
        
        String prompt = String.format(
            "%s\n\n我找到了匹配相似度最接近的 %d 条相关资料，由于结果较多无法精确分类，以下是第 %d 页的 %d 条结果：（💡 提示：您可以使用更具体的关键词重新搜索以获得更精准的结果）", 
            confirmText,
            pageInfo.getTotalResults(), 
            pageInfo.getCurrentPage(), 
            results.size()
        );
        
        return ChatResponseData.options(prompt, options);
    }
    
    /**
     * 构建分页响应
     */
    private Result<ChatResponseData> buildPaginatedResponse(String sessionId, 
            List<CircuitDocument> allResults, int totalCount, int page) {
        
        // 获取指定页的结果
        List<CircuitDocument> pageResults = conversationManager.getPageResults(sessionId, page);
        if (pageResults == null || pageResults.isEmpty()) {
            return Result.success(buildNoResultResponse());
        }
        
        ConversationManager.PageInfo pageInfo = conversationManager.getPageInfo(sessionId);
        if (pageInfo == null) {
            return Result.error("分页信息获取失败");
        }
        
        return Result.success(buildPaginatedOptionsResponse(pageResults, pageInfo));
    }
    
    /**
     * 构建分页选项响应
     */
    private ChatResponseData buildPaginatedOptionsResponse(List<CircuitDocument> results, 
            ConversationManager.PageInfo pageInfo) {
        
        List<Option> options = buildDocumentOptions(results);
        
        // 如果有下一页，添加"下一页"选项
        if (pageInfo.hasNextPage()) {
            options.add(new Option(
                options.size() + 1,
                String.format("📄 查看下一页（第%d页，共%d页）", 
                        pageInfo.getCurrentPage() + 1, pageInfo.getTotalPages()),
                "next_page"
            ));
        }
        
        String prompt = String.format(
            "我找到了匹配相似度最接近的 %d 条相关资料，由于结果较多无法精确分类，以下是第 %d 页的 %d 条结果：（💡 提示：您可以使用更具体的关键词重新搜索以获得更精准的结果）", 
            pageInfo.getTotalResults(), 
            pageInfo.getCurrentPage(), 
            results.size()
        );
        
        return ChatResponseData.options(prompt, options);
    }
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
     * 构建带警告的选项列表响应（用于无法分类的情况）
     * 格式：A. [ID: xxx] 文档标题
     */
    private ChatResponseData buildOptionsResponseWithWarning(List<CircuitDocument> results, int totalCount) {
        List<Option> options = buildDocumentOptions(results);
        
        String prompt = String.format(
            "我找到了 %d 条相关资料，由于结果较多无法精确分类，以下是最匹配的 %d 条：\n\n" +
            "💡 提示：您可以使用更具体的关键词重新搜索以获得更精准的结果。", 
            totalCount, results.size()
        );
        
        return ChatResponseData.options(prompt, options);
    }
    
    /**
     * 构建带确认语和警告的选项列表响应（用于筛选后仍无法分类的情况）
     */
    private ChatResponseData buildOptionsResponseWithConfirmAndWarning(List<CircuitDocument> results, 
            int totalCount, String confirmText) {
        List<Option> options = buildDocumentOptions(results);
        
        String prompt = String.format(
            "%s\n\n找到 %d 条相关资料，由于结果较多无法进一步分类，以下是最匹配的 %d 条：\n\n" +
            "💡 提示：如需更精确的结果，请重新搜索并使用更具体的关键词。", 
            confirmText, totalCount, results.size()
        );
        
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
