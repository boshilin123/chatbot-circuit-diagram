package com.bo.chatbot.model;

import lombok.Data;

/**
 * 聊天请求
 */
@Data
public class ChatRequest {
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 用户消息
     */
    private String message;
}
