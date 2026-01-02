package com.bo.chatbot.service;

import com.bo.chatbot.model.CircuitDocument;
import com.bo.chatbot.model.QueryInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器
 * 维护用户会话状态，支持多轮对话
 */
@Slf4j
@Service
public class ConversationManager {
    
    /**
     * 会话超时时间（分钟）
     */
    private static final int SESSION_TIMEOUT_MINUTES = 30;
    
    /**
     * 会话存储
     */
    private final Map<String, ConversationState> sessions = new ConcurrentHashMap<>();
    
    /**
     * 会话状态
     */
    @Data
    public static class ConversationState {
        private String sessionId;
        private QueryInfo lastQuery;                    // 上次查询信息
        private List<CircuitDocument> lastResults;      // 上次搜索结果
        private String lastCategoryType;                // 上次分类类型
        private LocalDateTime lastActiveTime;           // 最后活跃时间
        private int narrowingStep;                      // 缩小范围的步骤数
        
        public ConversationState(String sessionId) {
            this.sessionId = sessionId;
            this.lastActiveTime = LocalDateTime.now();
            this.narrowingStep = 0;
        }
        
        public void updateActiveTime() {
            this.lastActiveTime = LocalDateTime.now();
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(
                    lastActiveTime.plusMinutes(SESSION_TIMEOUT_MINUTES));
        }
    }
    
    /**
     * 获取或创建会话
     */
    public ConversationState getOrCreateSession(String sessionId) {
        // 清理过期会话
        cleanExpiredSessions();
        
        return sessions.computeIfAbsent(sessionId, ConversationState::new);
    }
    
    /**
     * 获取会话（不创建）
     */
    public ConversationState getSession(String sessionId) {
        ConversationState state = sessions.get(sessionId);
        if (state != null && !state.isExpired()) {
            state.updateActiveTime();
            return state;
        }
        return null;
    }
    
    /**
     * 保存搜索结果到会话
     */
    public void saveSearchResults(String sessionId, QueryInfo queryInfo, 
                                   List<CircuitDocument> results, String categoryType) {
        ConversationState state = getOrCreateSession(sessionId);
        state.setLastQuery(queryInfo);
        state.setLastResults(results);
        state.setLastCategoryType(categoryType);
        state.setNarrowingStep(0);
        state.updateActiveTime();
        
        log.debug("保存搜索结果到会话 - SessionId: {}, 结果数: {}", sessionId, results.size());
    }
    
    /**
     * 更新筛选后的结果
     */
    public void updateFilteredResults(String sessionId, List<CircuitDocument> filteredResults) {
        ConversationState state = getSession(sessionId);
        if (state != null) {
            state.setLastResults(filteredResults);
            state.setNarrowingStep(state.getNarrowingStep() + 1);
            state.updateActiveTime();
            
            log.debug("更新筛选结果 - SessionId: {}, 结果数: {}, 步骤: {}", 
                    sessionId, filteredResults.size(), state.getNarrowingStep());
        }
    }
    
    /**
     * 获取上次搜索结果
     */
    public List<CircuitDocument> getLastResults(String sessionId) {
        ConversationState state = getSession(sessionId);
        return state != null ? state.getLastResults() : null;
    }
    
    /**
     * 获取上次查询信息
     */
    public QueryInfo getLastQuery(String sessionId) {
        ConversationState state = getSession(sessionId);
        return state != null ? state.getLastQuery() : null;
    }
    
    /**
     * 清除会话
     */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        log.debug("清除会话 - SessionId: {}", sessionId);
    }
    
    /**
     * 清理过期会话
     */
    private void cleanExpiredSessions() {
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * 检查是否是分类选择
     */
    public boolean isCategorySelection(String message) {
        return message != null && message.startsWith("category:");
    }
    
    /**
     * 解析分类选择值
     */
    public String parseCategoryValue(String message) {
        if (message != null && message.startsWith("category:")) {
            return message.substring("category:".length());
        }
        return null;
    }
    
    /**
     * 获取会话统计
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("activeSessions", sessions.size());
        stats.put("timeoutMinutes", SESSION_TIMEOUT_MINUTES);
        return stats;
    }
}
