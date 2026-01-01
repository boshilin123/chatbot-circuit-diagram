package com.bo.chatbot.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 查询信息模型
 * 存储 AI 理解后的结构化信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryInfo {
    
    /**
     * 品牌（如：三一、徐工、红岩、卡特等）
     */
    private String brand;
    
    /**
     * 型号（如：SY215、杰狮、320D等）
     */
    private String model;
    
    /**
     * 部件类型（如：保险丝、仪表、ECU、液压等）
     */
    private String component;
    
    /**
     * ECU类型（如：CM2880、EDC7、DCM3.7等）
     */
    private String ecuType;
    
    /**
     * 查询类型（如：整车电路图、ECU电路图）
     */
    private String queryType;
    
    /**
     * 原始查询文本
     */
    private String originalQuery;
    
    /**
     * 判断是否有有效信息
     */
    public boolean hasValidInfo() {
        return brand != null || model != null || component != null || ecuType != null;
    }
    
    @Override
    public String toString() {
        return String.format("QueryInfo{brand='%s', model='%s', component='%s', ecuType='%s', queryType='%s'}",
                brand, model, component, ecuType, queryType);
    }
}
