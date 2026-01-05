package com.bo.chatbot.service;

import com.bo.chatbot.model.CircuitDocument;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据加载服务
 * 在应用启动时加载 CSV 文件到内存
 */
@Slf4j
@Service
public class DataLoaderService {
    
    /**
     * 内存中的文档列表
     */
    private List<CircuitDocument> documents = new ArrayList<>();
    
    /**
     * 应用启动时自动执行
     */
    @PostConstruct
    public void loadData() {
        log.info("开始加载电路图资料数据...");
        
        try {
            // 使用Spring的ClassPathResource加载文件,支持JAR内资源
            ClassPathResource resource = new ClassPathResource("资料清单.csv");
            
            if (!resource.exists()) {
                log.error("找不到资料清单.csv文件");
                return;
            }
            
            log.info("找到资料清单.csv文件,路径: {}", resource.getPath());
            
            // 读取 CSV 文件
            InputStream is = resource.getInputStream();
            
            // 使用 OpenCSV 解析
            CSVReader reader = new CSVReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            List<String[]> allRows = reader.readAll();
            reader.close();
            
            // 跳过表头，解析数据
            for (int i = 1; i < allRows.size(); i++) {
                String[] row = allRows.get(i);
                
                if (row.length >= 3) {
                    try {
                        Integer id = Integer.parseInt(row[0].trim());
                        String hierarchyPath = row[1].trim();
                        String fileName = row[2].trim();
                        
                        CircuitDocument doc = new CircuitDocument(id, hierarchyPath, fileName);
                        
                        // 提取关键词
                        extractKeywords(doc);
                        
                        documents.add(doc);
                    } catch (NumberFormatException e) {
                        log.warn("解析第 {} 行数据失败: {}", i + 1, e.getMessage());
                    }
                }
            }
            
            log.info("成功加载 {} 条电路图资料记录", documents.size());
            
            // 打印统计信息
            printStatistics();
            
        } catch (IOException | CsvException e) {
            log.error("加载数据失败", e);
        }
    }
    
    /**
     * 提取关键词
     */
    private void extractKeywords(CircuitDocument doc) {
        // 从层级路径提取
        if (doc.getHierarchyPath() != null) {
            String[] pathParts = doc.getHierarchyPath().split("->");
            for (String part : pathParts) {
                doc.addKeyword(part.trim());
            }
        }
        
        // 从文件名提取（简单分词）
        if (doc.getFileName() != null) {
            String fileName = doc.getFileName();
            // 按下划线和常见分隔符分割
            String[] nameParts = fileName.split("[_\\-、，]");
            for (String part : nameParts) {
                if (part.length() > 1) { // 过滤单字符
                    doc.addKeyword(part.trim());
                }
            }
        }
    }
    
    /**
     * 打印统计信息
     */
    private void printStatistics() {
        log.info("========== 数据统计 ==========");
        log.info("总文档数: {}", documents.size());
        
        // 统计品牌（简单统计）
        long sanyi = documents.stream().filter(d -> d.containsKeyword("三一")).count();
        long xugong = documents.stream().filter(d -> d.containsKeyword("徐工")).count();
        long kater = documents.stream().filter(d -> d.containsKeyword("卡特")).count();
        
        log.info("三一相关: {} 条", sanyi);
        log.info("徐工相关: {} 条", xugong);
        log.info("卡特相关: {} 条", kater);
        log.info("==============================");
    }
    
    /**
     * 获取所有文档
     */
    public List<CircuitDocument> getAllDocuments() {
        return new ArrayList<>(documents);
    }
    
    /**
     * 根据关键词搜索
     */
    public List<CircuitDocument> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        return documents.stream()
                .filter(doc -> doc.containsKeyword(keyword))
                .collect(Collectors.toList());
    }
    
    /**
     * 根据ID查询
     */
    public CircuitDocument getById(Integer id) {
        return documents.stream()
                .filter(doc -> doc.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取文档总数
     */
    public int getDocumentCount() {
        return documents.size();
    }
}
