package com.bo.chatbot.controller;

import com.bo.chatbot.model.*;
import com.bo.chatbot.service.*;
import com.bo.chatbot.config.AIConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private OptimizedQueryUnderstandingService optimizedQueryUnderstandingService;
    
    @Autowired
    private SmartSearchEngine smartSearchEngine;
    
    @Autowired
    private ResultCategorizer resultCategorizer;
    
    @Autowired
    private AIResultCategorizer aiResultCategorizer;
    
    @Autowired
    private AIConfig aiConfig;
    
    @Autowired
    private ConversationManager conversationManager;
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @Autowired
    private MonitoringService monitoringService;
    
    /**
     * 发送消息接口
     * POST /api/chat
     */
    @PostMapping("/chat")
    public Result<ChatResponseData> chat(@RequestBody ChatRequest request, 
                                       jakarta.servlet.http.HttpServletRequest httpRequest) {
        String sessionId = request.getSessionId();
        String message = request.getMessage();
        String clientIp = getClientIp(httpRequest);
        
        // 开始监控
        MonitoringService.RequestContext monitorContext = 
                monitoringService.startRequest(sessionId, message != null ? message : "", clientIp);
        
        try {
            log.info("收到聊天请求 - SessionId: {}, Message: {}", sessionId, message);
            
            // 验证参数
            if (sessionId == null || sessionId.trim().isEmpty()) {
                return Result.error("会话ID不能为空");
            }
            if (message == null || message.trim().isEmpty()) {
                return Result.error("消息内容不能为空");
            }
            
            message = message.trim();
            
            // 1. 请求限流检查
            RateLimitService.RateLimitResult rateLimitResult = 
                    rateLimitService.checkRateLimit(clientIp, sessionId);
            
            if (!rateLimitResult.isAllowed()) {
                log.warn("请求被限流 - IP: {}, Session: {}, Reason: {}", 
                        clientIp, sessionId, rateLimitResult.getReason());
                monitoringService.endRequest(monitorContext, false, "Rate limited: " + rateLimitResult.getReason());
                return Result.error(rateLimitResult.getMessage());
            }
            
            try {
                // 检查是否是问候或闲聊
                if (isGreetingOrChat(message)) {
                    Result<ChatResponseData> result = Result.success(buildWelcomeResponse());
                    monitoringService.endRequest(monitorContext, true, null);
                    return result;
                }
                
                // 首先检查搜索结果缓存
                List<CircuitDocument> cachedResults = cacheService.getCachedSearchResult(message);
                if (cachedResults != null) {
                    log.info("使用缓存的搜索结果 - Message: {}, 结果数: {}", message, cachedResults.size());
                    monitoringService.recordCacheEvent("SEARCH", true, message);
                    
                    // 即使使用缓存结果，也要尝试AI分类（如果结果数量较多）
                    QueryInfo cachedQueryInfo = new QueryInfo();
                    cachedQueryInfo.setOriginalQuery(message); // 设置原始查询用于AI分类
                    
                    // 保存到会话（使用缓存的结果）
                    conversationManager.saveSearchResults(sessionId, cachedQueryInfo, cachedResults, null);
                    Result<ChatResponseData> result = processSearchResults(sessionId, cachedResults, cachedQueryInfo, cachedResults.size());
                    monitoringService.endRequest(monitorContext, true, null);
                    return result;
                } else {
                    monitoringService.recordCacheEvent("SEARCH", false, message);
                }
                
                // 缓存未命中，进行查询理解（优化版）
                QueryInfo queryInfo = null;
                
                // 检查AI理解结果缓存
                QueryInfo cachedQueryInfo = cacheService.getCachedQueryInfo(message);
                if (cachedQueryInfo != null) {
                    queryInfo = cachedQueryInfo;
                    log.info("使用缓存的查询理解结果: {}", queryInfo);
                    monitoringService.recordCacheEvent("AI_QUERY", true, message);
                } else {
                    monitoringService.recordCacheEvent("AI_QUERY", false, message);
                    
                    // 2. AI请求限流检查（仅在需要AI处理时）
                    if (needsAIProcessing(message)) {
                        if (!rateLimitService.checkAiRequestLimit(clientIp)) {
                            log.warn("AI请求被限流 - IP: {}", clientIp);
                            monitoringService.recordAIEvent("AI_RATE_LIMITED", message, 0, false);
                            
                            // AI限流时降级到关键词搜索
                            long searchStart = System.currentTimeMillis();
                            List<CircuitDocument> results = smartSearchEngine.searchByKeyword(message);
                            long searchTime = System.currentTimeMillis() - searchStart;
                            
                            monitoringService.recordSearchEvent("KEYWORD_FALLBACK", results.size(), searchTime);
                            
                            if (results.isEmpty()) {
                                Result<ChatResponseData> result = Result.success(buildNoResultResponse());
                                monitoringService.endRequest(monitorContext, true, null);
                                return result;
                            }
                            cacheService.cacheSearchResult(message, results);
                            Result<ChatResponseData> result = processSearchResults(sessionId, results, null, results.size());
                            monitoringService.endRequest(monitorContext, true, null);
                            return result;
                        }
                    }
                    
                    // 使用优化的查询理解服务（智能选择本地/AI处理）
                    try {
                        long aiStart = System.currentTimeMillis();
                        queryInfo = optimizedQueryUnderstandingService.understand(message);
                        long aiTime = System.currentTimeMillis() - aiStart;
                        
                        log.info("查询理解结果: {}", queryInfo);
                        monitoringService.recordAIEvent("QUERY_UNDERSTANDING", message, aiTime, queryInfo != null);
                    } catch (Exception e) {
                        log.warn("查询理解失败，降级到关键词搜索", e);
                        monitoringService.recordException("QUERY_UNDERSTANDING_FAILED", e.getMessage(), e);
                    }
                }
                
                // 检查是否是无效查询
                if (queryInfo == null || !queryInfo.hasValidInfo()) {
                    // 尝试关键词搜索
                    long searchStart = System.currentTimeMillis();
                    List<CircuitDocument> results = smartSearchEngine.searchByKeyword(message);
                    long searchTime = System.currentTimeMillis() - searchStart;
                    
                    monitoringService.recordSearchEvent("KEYWORD", results.size(), searchTime);
                    
                    if (results.isEmpty()) {
                        Result<ChatResponseData> result = Result.success(buildNoResultResponse());
                        monitoringService.endRequest(monitorContext, true, null);
                        return result;
                    }
                    
                    // 缓存关键词搜索结果
                    cacheService.cacheSearchResult(message, results);
                    
                    Result<ChatResponseData> result = processSearchResults(sessionId, results, null, results.size());
                    monitoringService.endRequest(monitorContext, true, null);
                    return result;
                }
                
                // 保存原始查询
                queryInfo.setOriginalQuery(message);
                
                // 执行智能搜索
                long searchStart = System.currentTimeMillis();
                List<CircuitDocument> results = smartSearchEngine.search(queryInfo);
                long searchTime = System.currentTimeMillis() - searchStart;
                
                log.info("智能搜索 - QueryInfo: {}, 找到 {} 条结果", queryInfo, results.size());
                monitoringService.recordSearchEvent("SMART", results.size(), searchTime);
                
                // 缓存搜索结果
                cacheService.cacheSearchResult(message, results);
                
                // 保存到会话
                conversationManager.saveSearchResults(sessionId, queryInfo, results, null);
                
                // 处理搜索结果
                Result<ChatResponseData> result = processSearchResults(sessionId, results, queryInfo, results.size());
                monitoringService.endRequest(monitorContext, true, null);
                return result;
                
            } finally {
                // 3. 请求完成，减少并发计数
                rateLimitService.requestCompleted();
            }
            
        } catch (Exception e) {
            log.error("处理聊天请求失败", e);
            monitoringService.recordException("CHAT_REQUEST_FAILED", e.getMessage(), e);
            monitoringService.endRequest(monitorContext, false, e.getMessage());
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
        
        // 优先尝试AI分类（仅在AI可用时）
        AIResultCategorizer.AICategoryResult aiCategory = null;
        if (aiConfig.isAIEnabled()) {
            try {
                if (queryInfo != null && queryInfo.getOriginalQuery() != null) {
                    String originalQuery = queryInfo.getOriginalQuery();
                    log.info("尝试使用AI分类 - 查询: '{}'", originalQuery);
                    
                    // 先检查缓存
                    aiCategory = cacheService.getCachedAICategoryResult(originalQuery);
                    if (aiCategory != null) {
                        log.info("使用缓存的AI分类结果 - 分类数: {}", aiCategory.getOptions().size());
                    } else {
                        // 缓存未命中，调用AI分类
                        aiCategory = aiResultCategorizer.categorizeWithAI(results, originalQuery);
                        
                        // 缓存AI分类结果
                        if (aiCategory != null && aiCategory.getOptions().size() >= 2) {
                            cacheService.cacheAICategoryResult(originalQuery, aiCategory);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("AI分类失败，降级到传统分类: {}", e.getMessage());
            }
        } else {
            log.debug("AI功能未启用，跳过AI分类");
        }
        
        if (aiCategory != null && aiCategory.getOptions().size() >= 2) {
            // 使用AI分类结果
            log.info("✅ AI分类成功 - 生成 {} 个选项", aiCategory.getOptions().size());
            for (int i = 0; i < aiCategory.getOptions().size(); i++) {
                log.info("   {}. {}", i + 1, aiCategory.getOptions().get(i).getText());
            }
            
            // 保存AI分类结果到会话
            state.setAiCategoryMap(aiCategory.getCategoryMap());
            state.setLastCategoryType("ai_category");
            
            ChatResponseData data = ChatResponseData.options(aiCategory.getPrompt(), aiCategory.getOptions());
            return Result.success(data);
        } else {
            if (aiConfig.isAIEnabled()) {
                log.warn("❌ AI分类未生成有效选项，降级到传统分类");
            }
        }
        
        // 降级到传统分类
        // 获取原始查询用于智能分类
        String originalQuery = null;
        if (queryInfo != null && queryInfo.getOriginalQuery() != null) {
            originalQuery = queryInfo.getOriginalQuery();
        }
        
        ResultCategorizer.CategoryResult category = resultCategorizer.categorize(
                results, totalCount, state.getUsedCategoryTypes(), originalQuery);
        
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
            
            // 检查是否是AI分类选择（必须在传统分类检查之前）
            if (optionValue.startsWith("ai_category:")) {
                log.info("识别为AI分类选择: {}", optionValue);
                return handleCategorySelection(sessionId, optionValue);
            }
            
            // 检查是否是传统分类选择
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
        
        // 检查是否是AI分类
        if (state != null && "ai_category".equals(state.getLastCategoryType()) && 
            state.getAiCategoryMap() != null) {
            
            // 使用AI分类结果
            filtered = aiResultCategorizer.filterByAICategory(lastResults, categoryValue, state.getAiCategoryMap());
            actualCategoryType = "ai_category";
            
            log.info("AI分类筛选 - 类型: {}, 值: {}, 筛选前: {}, 筛选后: {}", 
                    actualCategoryType, categoryValue, lastResults.size(), 
                    filtered != null ? filtered.size() : 0);
        }
        
        // 如果AI分类没有结果，降级到传统分类
        if (filtered == null || filtered.isEmpty()) {
            // 优先在当前结果中筛选
            if (state != null) {
                // 首先尝试使用当前分类类型（最准确）
                String currentCategoryType = state.getLastCategoryType();
                if (currentCategoryType != null && !"ai_category".equals(currentCategoryType)) {
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
     * 支持AI多次分类以获得精准结果
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
        
        // >5条：检查是否是AI分类的结果，如果是则继续AI分类
        boolean isAICategory = state != null && "ai_category".equals(state.getLastCategoryType());
        
        if (isAICategory && aiConfig.isAIEnabled()) {
            // AI分类后结果仍>5条，继续使用AI进行二次分类
            log.info("AI分类筛选后仍有 {} 条结果，尝试AI二次分类", results.size());
            
            try {
                // 获取原始查询
                String originalQuery = null;
                if (state != null) {
                    QueryInfo lastQuery = conversationManager.getLastQuery(sessionId);
                    if (lastQuery != null && lastQuery.getOriginalQuery() != null) {
                        originalQuery = lastQuery.getOriginalQuery();
                    }
                }
                
                if (originalQuery != null) {
                    // 构建二次分类的查询提示（加上已选择的分类信息）
                    String refinedQuery = originalQuery + " " + selectedCategory;
                    
                    // 先检查缓存
                    AIResultCategorizer.AICategoryResult aiCategory = cacheService.getCachedAICategoryResult(refinedQuery);
                    
                    if (aiCategory == null) {
                        // 缓存未命中，调用AI进行二次分类
                        aiCategory = aiResultCategorizer.categorizeWithAI(results, refinedQuery);
                        
                        // 缓存AI分类结果
                        if (aiCategory != null && aiCategory.getOptions().size() >= 2) {
                            cacheService.cacheAICategoryResult(refinedQuery, aiCategory);
                        }
                    }
                    
                    if (aiCategory != null && aiCategory.getOptions().size() >= 2) {
                        // AI二次分类成功
                        log.info("✅ AI二次分类成功 - 生成 {} 个选项", aiCategory.getOptions().size());
                        
                        // 保存AI分类结果到会话
                        state.setAiCategoryMap(aiCategory.getCategoryMap());
                        state.setLastCategoryType("ai_category");
                        
                        String prompt = confirmText + "\n\n" + aiCategory.getPrompt();
                        ChatResponseData data = ChatResponseData.options(prompt, aiCategory.getOptions());
                        return Result.success(data);
                    } else {
                        log.warn("AI二次分类未生成有效选项，使用分页显示");
                    }
                }
            } catch (Exception e) {
                log.warn("AI二次分类失败: {}", e.getMessage());
            }
            
            // AI二次分类失败，使用分页显示
            if (state != null) {
                state.setAllResults(new ArrayList<>(results));
                state.setCurrentPage(0);
            }
            
            return buildPaginatedResponseWithConfirm(sessionId, results, confirmText);
        }
        
        // 传统分类：继续分类引导
        
        // 获取原始查询用于智能分类
        String originalQuery = null;
        if (state != null) {
            QueryInfo lastQuery = conversationManager.getLastQuery(sessionId);
            if (lastQuery != null && lastQuery.getOriginalQuery() != null) {
                originalQuery = lastQuery.getOriginalQuery();
            }
        }
        
        ResultCategorizer.CategoryResult category = resultCategorizer.categorize(
                results, results.size(), state != null ? state.getUsedCategoryTypes() : new HashSet<>(), originalQuery);
        
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
            "您好！我是智能车辆电路图资料导航助手 ✨\n\n" +
            "🎯 拥有 4000+ 条电路图资料，采用智能搜索技术\n" +
            "⚡ 简单查询秒级响应，复杂问题AI理解\n" +
            "📋 支持历史记录，方便随时查看\n\n" +
            "💡 推荐搜索示例：\n" +
            "三菱4K22\n" +
            "红岩杰狮保险丝\n" +
            "东风天龙仪表\n\n" +
            "请输入您要查找的内容，我来帮您快速定位！😊"
        );
    }
    
    /**
     * 构建无结果响应
     */
    private ChatResponseData buildNoResultResponse() {
        return ChatResponseData.text(
            "😅 抱歉，没有找到相关资料呢...\n\n" +
            "💡 **建议您试试**：\n" +
            "🔍 检查品牌或型号是否正确\n" +
            "🎯 使用更通用的关键词\n" +
            "✨ 换一种表达方式\n\n" +
            "📝 **搜索小贴士**：\n" +
            "• 简单明确：\"三一挖掘机\" \"红岩保险丝\" 🚛\n" +
            "• 包含型号：\"东风天龙KL\" \"康明斯C240\" 🔧\n" +
            "• 指定部件：\"仪表针脚图\" \"ECU电路图\" ⚡\n\n" +
            "再试一次吧！我相信能帮您找到需要的资料 💪"
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
            "你是谁", "你是什么", "介绍一下", "谢谢",
            "早上好", "下午好", "晚上好", "早安", "晚安"
        };
        
        String lowerMessage = message.toLowerCase().trim();
        
        // 先检查是否包含电路图相关词汇，如果包含则不是闲聊
        String[] keywords = {"电路", "图", "保险", "仪表", "ECU", "线路", "天龙", "杰狮", "三一", "徐工", "卡特", "康明斯", "针脚", "定义", "资料", "找", "查", "搜索"};
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return false; // 包含专业词汇，不是闲聊
            }
        }
        
        // 检查问候语
        for (String greeting : greetings) {
            if (lowerMessage.contains(greeting)) {
                return true;
            }
        }
        
        // 如果消息很短且不包含电路图相关词汇，也认为是闲聊
        if (message.length() <= 5) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 处理多个IP的情况，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip != null ? ip : "unknown";
    }
    
    /**
     * 判断查询是否需要AI处理
     */
    private boolean needsAIProcessing(String message) {
        // 简单的启发式判断
        String lowerMessage = message.toLowerCase();
        
        // 包含自然语言表达的可能需要AI
        String[] aiIndicators = {
            "帮我找", "我需要", "请给我", "能否", "可以", "有没有",
            "关于", "相关", "一些", "几个", "什么", "怎么"
        };
        
        for (String indicator : aiIndicators) {
            if (lowerMessage.contains(indicator)) {
                return true;
            }
        }
        
        // 长句子可能需要AI理解
        return message.length() > 15;
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
            
            // 添加缓存统计信息
            CacheService.CacheStats cacheStats = cacheService.getStats();
            java.util.Map<String, Object> cacheInfo = new java.util.HashMap<>();
            cacheInfo.put("searchCacheSize", cacheStats.getSearchCacheSize());
            cacheInfo.put("aiCacheSize", cacheStats.getAiCacheSize());
            cacheInfo.put("totalCacheSize", cacheStats.getTotalCacheSize());
            cacheInfo.put("searchHitRate", String.format("%.2f%%", cacheStats.getSearchHitRate() * 100));
            cacheInfo.put("aiHitRate", String.format("%.2f%%", cacheStats.getAiHitRate() * 100));
            cacheInfo.put("searchHitCount", cacheStats.getSearchHitCount());
            cacheInfo.put("searchMissCount", cacheStats.getSearchMissCount());
            cacheInfo.put("aiHitCount", cacheStats.getAiHitCount());
            cacheInfo.put("aiMissCount", cacheStats.getAiMissCount());
            stats.put("cache", cacheInfo);
            
            // 添加查询处理统计信息
            OptimizedQueryUnderstandingService.ProcessingStats processingStats = 
                    optimizedQueryUnderstandingService.getStats();
            java.util.Map<String, Object> processingInfo = new java.util.HashMap<>();
            processingInfo.put("localProcessCount", processingStats.getLocalProcessCount());
            processingInfo.put("aiProcessCount", processingStats.getAiProcessCount());
            processingInfo.put("aiFailureCount", processingStats.getAiFailureCount());
            processingInfo.put("totalProcessCount", processingStats.getTotalProcessCount());
            processingInfo.put("localProcessRate", String.format("%.2f%%", processingStats.getLocalProcessRate() * 100));
            processingInfo.put("aiProcessRate", String.format("%.2f%%", processingStats.getAiProcessRate() * 100));
            processingInfo.put("aiSuccessRate", String.format("%.2f%%", processingStats.getAiSuccessRate() * 100));
            stats.put("queryProcessing", processingInfo);
            
            // 添加限流统计信息
            RateLimitService.RateLimitStats rateLimitStats = rateLimitService.getStats();
            java.util.Map<String, Object> rateLimitInfo = new java.util.HashMap<>();
            rateLimitInfo.put("totalRequests", rateLimitStats.getTotalRequests());
            rateLimitInfo.put("blockedRequests", rateLimitStats.getBlockedRequests());
            rateLimitInfo.put("aiRequestsBlocked", rateLimitStats.getAiRequestsBlocked());
            rateLimitInfo.put("currentConcurrentRequests", rateLimitStats.getCurrentConcurrentRequests());
            rateLimitInfo.put("activeIpRecords", rateLimitStats.getActiveIpRecords());
            rateLimitInfo.put("activeSessionRecords", rateLimitStats.getActiveSessionRecords());
            rateLimitInfo.put("activeAiRecords", rateLimitStats.getActiveAiRecords());
            rateLimitInfo.put("blockedRate", String.format("%.2f%%", rateLimitStats.getBlockedRate() * 100));
            rateLimitInfo.put("aiBlockedRate", String.format("%.2f%%", rateLimitStats.getAiBlockedRate() * 100));
            stats.put("rateLimit", rateLimitInfo);
            
            // 添加监控统计信息
            MonitoringService.SystemHealth systemHealth = monitoringService.getSystemHealth();
            java.util.Map<String, Object> monitoringInfo = new java.util.HashMap<>();
            monitoringInfo.put("healthStatus", systemHealth.getStatus().getDescription());
            monitoringInfo.put("avgResponseTime", String.format("%.2f", systemHealth.getAvgResponseTime()));
            monitoringInfo.put("errorRate", String.format("%.2f%%", systemHealth.getErrorRate() * 100));
            monitoringInfo.put("slowQueryRate", String.format("%.2f%%", systemHealth.getSlowQueryRate() * 100));
            monitoringInfo.put("totalRequests", systemHealth.getTotalRequests());
            monitoringInfo.put("errorCount", systemHealth.getErrorCount());
            monitoringInfo.put("slowQueryCount", systemHealth.getSlowQueryCount());
            stats.put("monitoring", monitoringInfo);
            
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return Result.error("获取统计信息失败");
        }
    }
    
    /**
     * 获取查询处理统计接口
     */
    @GetMapping("/query/stats")
    public Result<OptimizedQueryUnderstandingService.ProcessingStats> getQueryStats() {
        try {
            return Result.success(optimizedQueryUnderstandingService.getStats());
        } catch (Exception e) {
            log.error("获取查询处理统计失败", e);
            return Result.error("获取查询处理统计失败");
        }
    }
    
    /**
     * 重置查询处理统计接口
     */
    @PostMapping("/query/stats/reset")
    public Result<String> resetQueryStats() {
        try {
            optimizedQueryUnderstandingService.resetStats();
            return Result.success("查询处理统计已重置");
        } catch (Exception e) {
            log.error("重置查询处理统计失败", e);
            return Result.error("重置查询处理统计失败");
        }
    }
    
    /**
     * 测试查询复杂度分析接口
     */
    @PostMapping("/query/analyze")
    public Result<Object> analyzeQuery(@RequestBody java.util.Map<String, String> request) {
        try {
            String query = request.get("query");
            if (query == null || query.trim().isEmpty()) {
                return Result.error("查询内容不能为空");
            }
            
            // 这里需要注入QueryComplexityAnalyzer，暂时返回简单信息
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("query", query);
            result.put("message", "查询复杂度分析功能需要进一步集成");
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("分析查询复杂度失败", e);
            return Result.error("分析查询复杂度失败");
        }
    }
    
    /**
     * 清空缓存接口
     */
    @PostMapping("/cache/clear")
    public Result<String> clearCache() {
        try {
            cacheService.clearAllCache();
            return Result.success("缓存已清空");
        } catch (Exception e) {
            log.error("清空缓存失败", e);
            return Result.error("清空缓存失败");
        }
    }
    
    /**
     * 获取缓存统计接口
     */
    @GetMapping("/cache/stats")
    public Result<CacheService.CacheStats> getCacheStats() {
        try {
            return Result.success(cacheService.getStats());
        } catch (Exception e) {
            log.error("获取缓存统计失败", e);
            return Result.error("获取缓存统计失败");
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
     * 获取系统健康状态接口
     */
    @GetMapping("/health")
    public Result<MonitoringService.SystemHealth> getSystemHealth() {
        try {
            return Result.success(monitoringService.getSystemHealth());
        } catch (Exception e) {
            log.error("获取系统健康状态失败", e);
            return Result.error("获取系统健康状态失败");
        }
    }
    
    /**
     * 获取性能趋势接口
     */
    @GetMapping("/performance/trend")
    public Result<java.util.Map<String, MonitoringService.PerformanceRecord>> getPerformanceTrend() {
        try {
            return Result.success(monitoringService.getPerformanceTrend());
        } catch (Exception e) {
            log.error("获取性能趋势失败", e);
            return Result.error("获取性能趋势失败");
        }
    }
    
    /**
     * 重置监控统计接口
     */
    @PostMapping("/monitoring/stats/reset")
    public Result<String> resetMonitoringStats() {
        try {
            monitoringService.resetStats();
            return Result.success("监控统计已重置");
        } catch (Exception e) {
            log.error("重置监控统计失败", e);
            return Result.error("重置监控统计失败");
        }
    }
    
    /**
     * 获取限流统计接口
     */
    @GetMapping("/ratelimit/stats")
    public Result<RateLimitService.RateLimitStats> getRateLimitStats() {
        try {
            return Result.success(rateLimitService.getStats());
        } catch (Exception e) {
            log.error("获取限流统计失败", e);
            return Result.error("获取限流统计失败");
        }
    }
    
    /**
     * 重置限流统计接口
     */
    @PostMapping("/ratelimit/stats/reset")
    public Result<String> resetRateLimitStats() {
        try {
            rateLimitService.resetStats();
            return Result.success("限流统计已重置");
        } catch (Exception e) {
            log.error("重置限流统计失败", e);
            return Result.error("重置限流统计失败");
        }
    }
    
    /**
     * 测试DeepSeek API连接
     */
    @GetMapping("/test/deepseek-api")
    public Result<String> testDeepSeekAPI() {
        try {
            String result = aiResultCategorizer.testAPIConnection();
            return Result.success(result);
        } catch (Exception e) {
            log.error("测试DeepSeek API失败", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试 AI 分类接口
     */
    @PostMapping("/test/ai-categorize")
    public Result<Object> testAICategorize(@RequestBody Map<String, String> request) {
        try {
            String query = request.get("query");
            if (query == null || query.trim().isEmpty()) {
                return Result.error("查询内容不能为空");
            }
            
            // 执行搜索获取结果
            List<CircuitDocument> results = smartSearchEngine.searchByKeyword(query);
            
            if (results.isEmpty()) {
                return Result.error("没有找到相关资料");
            }
            
            // 调用AI分类
            AIResultCategorizer.AICategoryResult aiResult = aiResultCategorizer.categorizeWithAI(results, query);
            
            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("totalResults", results.size());
            
            if (aiResult != null) {
                response.put("aiCategorizeSuccess", true);
                response.put("prompt", aiResult.getPrompt());
                response.put("options", aiResult.getOptions());
                response.put("categoryCount", aiResult.getOptions().size());
            } else {
                response.put("aiCategorizeSuccess", false);
                response.put("message", "AI分类失败");
            }
            
            return Result.success(response);
            
        } catch (Exception e) {
            log.error("测试AI分类失败", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }
}
