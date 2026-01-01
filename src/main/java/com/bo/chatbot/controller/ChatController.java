package com.bo.chatbot.controller;

import com.bo.chatbot.model.*;
import com.bo.chatbot.service.DataLoaderService;
import com.bo.chatbot.service.QueryUnderstandingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天 API 控制器
 * 处理前端的聊天请求
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class ChatController {
    
    @Autowired
    private DataLoaderService dataLoaderService;
    
    @Autowired
    private QueryUnderstandingService queryUnderstandingService;
    
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
            
            String message = request.getMessage().trim();
            
            // ========== Day 2 新增：使用 AI 理解用户查询 ==========
            QueryInfo queryInfo = null;
            try {
                queryInfo = queryUnderstandingService.understand(message);
                log.info("AI 理解结果: {}", queryInfo);
            } catch (Exception e) {
                log.warn("AI 理解失败，降级到关键词搜索", e);
            }
            
            // 检查是否是非查询消息（闲聊、问候等）
            if (queryInfo == null || !queryInfo.hasValidInfo()) {
                // 检查是否是问候或闲聊
                if (isGreetingOrChat(message)) {
                    ChatResponseData data = ChatResponseData.text(
                        "您好！我是电路图资料助手 🚗\n\n" +
                        "我可以帮您查找车辆电路图资料，请输入您要查找的内容，例如：\n" +
                        "• \"红岩杰狮保险丝\"\n" +
                        "• \"三一挖掘机仪表\"\n" +
                        "• \"康明斯2880电路图\"\n\n" +
                        "请问您需要查找什么资料？"
                    );
                    return Result.success(data);
                }
            }
            
            // 搜索相关文档
            List<CircuitDocument> results;
            if (queryInfo != null && queryInfo.hasValidInfo()) {
                // 使用 AI 理解的信息进行搜索
                results = searchWithQueryInfo(queryInfo);
            } else {
                // 降级到关键词搜索
                results = dataLoaderService.search(message);
            }
            
            log.info("搜索关键词: {}, 找到 {} 条结果", message, results.size());
            
            // 根据结果数量返回不同响应
            if (results.isEmpty()) {
                // 未找到结果
                ChatResponseData data = ChatResponseData.text(
                    "抱歉，未找到相关资料。\n\n建议您：\n" +
                    "1. 检查品牌或型号是否正确\n" +
                    "2. 尝试使用更通用的关键词\n" +
                    "3. 换一种表达方式\n\n" +
                    "例如：\"三一挖掘机\"、\"红岩保险丝\"、\"康明斯ECU\""
                );
                return Result.success(data);
                
            } else if (results.size() == 1) {
                // 找到唯一结果
                CircuitDocument doc = results.get(0);
                ChatResponseData data = ChatResponseData.result(
                    "✅ 已为您找到匹配的资料：",
                    doc
                );
                return Result.success(data);
                
            } else if (results.size() <= 5) {
                // 找到少量结果，返回选择题
                List<Option> options = new ArrayList<>();
                for (int i = 0; i < results.size(); i++) {
                    CircuitDocument doc = results.get(i);
                    // 显示层级路径 + 文件名，方便用户区分
                    String displayText = buildDisplayText(doc);
                    options.add(new Option(
                        i + 1,
                        displayText,
                        doc.getId().toString()
                    ));
                }
                
                ChatResponseData data = ChatResponseData.options(
                    String.format("找到 %d 条相关资料，请选择您需要的：", results.size()),
                    options
                );
                return Result.success(data);
                
            } else {
                // 找到大量结果，建议缩小范围
                // 取前5个作为示例
                List<Option> options = results.stream()
                        .limit(5)
                        .map(doc -> new Option(
                            doc.getId(),
                            buildDisplayText(doc),
                            doc.getId().toString()
                        ))
                        .collect(Collectors.toList());
                
                ChatResponseData data = ChatResponseData.options(
                    String.format("找到 %d 条相关资料，结果较多。\n" +
                                "以下是部分匹配结果，请选择或尝试更具体的关键词：", 
                                results.size()),
                    options
                );
                return Result.success(data);
            }
            
        } catch (Exception e) {
            log.error("处理聊天请求失败", e);
            return Result.error("系统繁忙，请稍后重试");
        }
    }
    
    /**
     * 使用 QueryInfo 进行搜索
     * Day 3 会实现更智能的搜索算法
     */
    private List<CircuitDocument> searchWithQueryInfo(QueryInfo queryInfo) {
        List<CircuitDocument> results = new ArrayList<>();
        
        // 目前简单实现：将 QueryInfo 的各个字段作为关键词搜索
        if (queryInfo.getBrand() != null) {
            results.addAll(dataLoaderService.search(queryInfo.getBrand()));
        }
        if (queryInfo.getModel() != null) {
            results.addAll(dataLoaderService.search(queryInfo.getModel()));
        }
        if (queryInfo.getComponent() != null) {
            results.addAll(dataLoaderService.search(queryInfo.getComponent()));
        }
        if (queryInfo.getEcuType() != null) {
            results.addAll(dataLoaderService.search(queryInfo.getEcuType()));
        }
        
        // 去重
        return results.stream()
                .distinct()
                .collect(Collectors.toList());
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
     * 构建选项显示文本
     * 从层级路径中提取关键信息，帮助用户区分相同文件名的文档
     */
    private String buildDisplayText(CircuitDocument doc) {
        String fileName = doc.getFileName();
        String path = doc.getHierarchyPath();
        
        if (path == null || path.isEmpty()) {
            return fileName;
        }
        
        // 从层级路径中提取关键信息（取最后2-3级）
        String[] parts = path.split("->");
        if (parts.length >= 2) {
            // 取倒数第2级作为分类信息
            String category = parts[parts.length - 1].trim();
            // 如果分类和文件名不同，显示分类
            if (!fileName.contains(category)) {
                return String.format("[%s] %s", category, fileName);
            }
        }
        
        // 如果层级路径较长，显示简化版本
        if (parts.length >= 3) {
            String shortPath = parts[parts.length - 2].trim() + " > " + parts[parts.length - 1].trim();
            return String.format("%s (%s)", fileName, shortPath);
        }
        
        return fileName;
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
            // 验证参数
            if (request.getOptionValue() == null || request.getOptionValue().trim().isEmpty()) {
                return Result.error("选项值不能为空");
            }
            
            // 根据选项值（文档ID）查询文档
            Integer docId = Integer.parseInt(request.getOptionValue());
            CircuitDocument doc = dataLoaderService.getById(docId);
            
            if (doc == null) {
                return Result.error("未找到对应的文档");
            }
            
            // 返回最终结果
            ChatResponseData data = ChatResponseData.result(
                "✅ 已为您找到匹配的资料：",
                doc
            );
            return Result.success(data);
            
        } catch (NumberFormatException e) {
            log.error("解析文档ID失败", e);
            return Result.error("无效的文档ID");
        } catch (Exception e) {
            log.error("处理选择请求失败", e);
            return Result.error("系统繁忙，请稍后重试");
        }
    }
    
    /**
     * 获取统计信息接口
     * GET /api/stats
     */
    @GetMapping("/stats")
    public Result<Object> getStats() {
        try {
            int totalCount = dataLoaderService.getDocumentCount();
            
            // 简单统计
            java.util.Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("totalDocuments", totalCount);
            stats.put("status", "运行中");
            stats.put("message", "数据加载成功");
            
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return Result.error("获取统计信息失败");
        }
    }
    
    /**
     * 根据ID查询文档接口
     * GET /api/document/{id}
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
     * POST /api/test/understand
     */
    @PostMapping("/test/understand")
    public Result<QueryInfo> testUnderstand(@RequestBody ChatRequest request) {
        try {
            String message = request.getMessage();
            log.info("测试 AI 理解: {}", message);
            
            QueryInfo queryInfo = queryUnderstandingService.understand(message);
            
            return Result.success(queryInfo);
        } catch (Exception e) {
            log.error("测试 AI 理解失败", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }
}
