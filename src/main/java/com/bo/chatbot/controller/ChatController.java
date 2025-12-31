package com.bo.chatbot.controller;

import com.bo.chatbot.model.*;
import com.bo.chatbot.service.DataLoaderService;
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
            
            // 搜索相关文档
            List<CircuitDocument> results = dataLoaderService.search(message);
            
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
                    options.add(new Option(
                        i + 1,
                        doc.getFileName(),
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
                            doc.getFileName(),
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
}
