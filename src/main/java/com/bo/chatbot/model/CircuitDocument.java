package com.bo.chatbot.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 电路图文档实体类
 * 对应 CSV 文件中的一条记录
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CircuitDocument {
    
    /**
     * 文档ID
     */
    private Integer id;
    
    /**
     * 层级路径
     * 例如：电路图->ECU电路图->工程机械->三一->德国仪表
     */
    private String hierarchyPath;
    
    /**
     * 文件名称
     * 例如：三一挖掘机_德国仪表显示器针脚定义
     */
    private String fileName;
    
    /**
     * 关键词列表（从层级路径和文件名提取）
     */
    private List<String> keywords;
    
    /**
     * 构造函数（不含关键词）
     */
    public CircuitDocument(Integer id, String hierarchyPath, String fileName) {
        this.id = id;
        this.hierarchyPath = hierarchyPath;
        this.fileName = fileName;
        this.keywords = new ArrayList<>();
    }
    
    /**
     * 添加关键词
     */
    public void addKeyword(String keyword) {
        if (this.keywords == null) {
            this.keywords = new ArrayList<>();
        }
        if (!this.keywords.contains(keyword)) {
            this.keywords.add(keyword);
        }
    }
    
    /**
     * 检查是否包含关键词
     */
    public boolean containsKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return false;
        }
        
        String lowerKeyword = keyword.toLowerCase();
        
        // 在层级路径中查找
        if (hierarchyPath != null && hierarchyPath.toLowerCase().contains(lowerKeyword)) {
            return true;
        }
        
        // 在文件名中查找
        if (fileName != null && fileName.toLowerCase().contains(lowerKeyword)) {
            return true;
        }
        
        // 在关键词列表中查找
        if (keywords != null) {
            for (String kw : keywords) {
                if (kw.toLowerCase().contains(lowerKeyword)) {
                    return true;
                }
            }
        }
        
        return false;
    }
}
