package com.bo.chatbot.model;

import lombok.Data;

/**
 * 选择请求
 */
@Data
public class SelectRequest {
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 选项ID
     */
    private Integer optionId;
    
    /**
     * 选项值
     */
    private String optionValue;
}
