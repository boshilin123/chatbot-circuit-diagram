package com.bo.chatbot.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 选择题选项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Option {
    
    /**
     * 选项ID
     */
    private Integer id;
    
    /**
     * 显示文本
     */
    private String text;
    
    /**
     * 选项值（通常是文档ID）
     */
    private String value;
}
