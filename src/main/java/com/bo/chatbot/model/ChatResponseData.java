package com.bo.chatbot.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聊天响应数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseData {
    
    /**
     * 响应类型：text, options, result
     */
    private String type;
    
    /**
     * 文本内容
     */
    private String content;
    
    /**
     * 选择题选项列表
     */
    private List<Option> options;
    
    /**
     * 电路图文档信息
     */
    private CircuitDocument document;
    
    /**
     * 创建文本消息
     */
    public static ChatResponseData text(String content) {
        return new ChatResponseData("text", content, null, null);
    }
    
    /**
     * 创建选择题
     */
    public static ChatResponseData options(String content, List<Option> options) {
        return new ChatResponseData("options", content, options, null);
    }
    
    /**
     * 创建结果消息
     */
    public static ChatResponseData result(String content, CircuitDocument document) {
        return new ChatResponseData("result", content, null, document);
    }
}
