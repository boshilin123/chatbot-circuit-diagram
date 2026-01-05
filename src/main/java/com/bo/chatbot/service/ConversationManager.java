package com.bo.chatbot.service;

import com.bo.chatbot.model.CircuitDocument;
import com.bo.chatbot.model.QueryInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
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
        private java.util.Set<String> usedCategoryTypes; // 已使用的分类类型
        private LocalDateTime lastActiveTime;           // 最后活跃时间
        private int narrowingStep;                      // 缩小范围的步骤数
        
        // 新增：状态历史记录，支持返回上一步
        private java.util.List<StateSnapshot> stateHistory; // 状态历史
        
        // 新增：分页相关字段
        private List<CircuitDocument> allResults;       // 完整搜索结果（用于分页）
        private int currentPage;                        // 当前页码（从0开始）
        private int pageSize;                          // 每页大小
        
        // 新增：AI分类相关字段
        private Map<String, List<CircuitDocument>> aiCategoryMap; // AI分类结果映射
        
        public ConversationState(String sessionId) {
            this.sessionId = sessionId;
            this.lastActiveTime = LocalDateTime.now();
            this.narrowingStep = 0;
            this.usedCategoryTypes = new java.util.HashSet<>();
            this.stateHistory = new java.util.ArrayList<>();
            this.currentPage = 0;
            this.pageSize = 5; // 默认每页5条
        }
        
        public void updateActiveTime() {
            this.lastActiveTime = LocalDateTime.now();
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(
                    lastActiveTime.plusMinutes(SESSION_TIMEOUT_MINUTES));
        }
        
        /**
         * 添加已使用的分类类型
         */
        public void addUsedCategoryType(String categoryType) {
            if (categoryType != null) {
                this.usedCategoryTypes.add(categoryType);
            }
        }
        
        /**
         * 重置已使用的分类类型（新搜索时调用）
         */
        public void resetUsedCategoryTypes() {
            this.usedCategoryTypes.clear();
        }
        
        /**
         * 获取已使用的分类类型
         */
        public java.util.Set<String> getUsedCategoryTypes() {
            return this.usedCategoryTypes;
        }
        
        /**
         * 保存当前状态快照
         */
        public void saveStateSnapshot() {
            StateSnapshot snapshot = new StateSnapshot(
                this.lastResults != null ? new java.util.ArrayList<>(this.lastResults) : null,
                this.lastCategoryType,
                new java.util.HashSet<>(this.usedCategoryTypes),
                this.narrowingStep
            );
            this.stateHistory.add(snapshot);
            
            // 限制历史记录数量，避免内存泄漏
            if (this.stateHistory.size() > 10) {
                this.stateHistory.remove(0);
            }
        }
        
        /**
         * 返回上一步
         */
        public boolean goBack() {
            if (this.stateHistory.isEmpty()) {
                return false;
            }
            
            // 获取上一个状态
            StateSnapshot lastSnapshot = this.stateHistory.remove(this.stateHistory.size() - 1);
            
            // 恢复状态
            this.lastResults = lastSnapshot.results;
            this.lastCategoryType = lastSnapshot.categoryType;
            this.usedCategoryTypes = new java.util.HashSet<>(lastSnapshot.usedCategoryTypes);
            this.narrowingStep = lastSnapshot.narrowingStep;
            
            return true;
        }
        
        /**
         * 返回到指定步骤
         */
        public boolean goBackToStep(int targetStep) {
            if (this.stateHistory.isEmpty() || targetStep < 0) {
                return false;
            }
            
            // 计算需要回退的步数
            int currentStep = this.narrowingStep;
            int stepsToGoBack = currentStep - targetStep;
            
            if (stepsToGoBack <= 0 || stepsToGoBack > this.stateHistory.size()) {
                return false;
            }
            
            // 连续回退到目标步骤
            for (int i = 0; i < stepsToGoBack; i++) {
                if (!goBack()) {
                    return false;
                }
            }
            
            return true;
        }
        
        /**
         * 智能回退：根据选项值判断应该回退到哪一步
         */
        public boolean smartGoBack(String categoryValue) {
            // 简单策略：如果当前步骤>1，尝试回退到第0步（原始结果）
            if (this.narrowingStep > 1 && this.stateHistory.size() >= this.narrowingStep) {
                return goBackToStep(0);
            }
            // 否则回退一步
            return goBack();
        }
        
        /**
         * 检查是否可以返回上一步
         */
        public boolean canGoBack() {
            return !this.stateHistory.isEmpty();
        }
    }
    
    /**
     * 状态快照，用于支持返回上一步
     */
    @Data
    public static class StateSnapshot {
        private final List<CircuitDocument> results;
        private final String categoryType;
        private final java.util.Set<String> usedCategoryTypes;
        private final int narrowingStep;
        
        public StateSnapshot(List<CircuitDocument> results, String categoryType, 
                           java.util.Set<String> usedCategoryTypes, int narrowingStep) {
            this.results = results;
            this.categoryType = categoryType;
            this.usedCategoryTypes = usedCategoryTypes;
            this.narrowingStep = narrowingStep;
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
     * 保存搜索结果到会话（新搜索，重置已使用的分类类型）
     */
    public void saveSearchResults(String sessionId, QueryInfo queryInfo, 
                                   List<CircuitDocument> results, String categoryType) {
        ConversationState state = getOrCreateSession(sessionId);
        state.setLastQuery(queryInfo);
        state.setLastResults(results);
        state.setAllResults(new ArrayList<>(results)); // 保存完整结果用于分页
        state.setLastCategoryType(categoryType);
        state.setNarrowingStep(0);
        state.setCurrentPage(0); // 重置分页
        state.resetUsedCategoryTypes(); // 新搜索时重置
        state.updateActiveTime();
        
        log.debug("保存搜索结果到会话 - SessionId: {}, 结果数: {}", sessionId, results.size());
    }
    
    /**
     * 获取指定页的结果
     */
    public List<CircuitDocument> getPageResults(String sessionId, int page) {
        ConversationState state = getSession(sessionId);
        if (state == null || state.getAllResults() == null) {
            return null;
        }
        
        List<CircuitDocument> allResults = state.getAllResults();
        int pageSize = state.getPageSize();
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allResults.size());
        
        if (startIndex >= allResults.size()) {
            return Collections.emptyList();
        }
        
        state.setCurrentPage(page);
        state.updateActiveTime();
        
        return allResults.subList(startIndex, endIndex);
    }
    
    /**
     * 获取下一页结果
     */
    public List<CircuitDocument> getNextPageResults(String sessionId) {
        ConversationState state = getSession(sessionId);
        if (state == null) {
            return null;
        }
        
        int nextPage = state.getCurrentPage() + 1;
        return getPageResults(sessionId, nextPage);
    }
    
    /**
     * 检查是否有下一页
     */
    public boolean hasNextPage(String sessionId) {
        ConversationState state = getSession(sessionId);
        if (state == null || state.getAllResults() == null) {
            return false;
        }
        
        int totalResults = state.getAllResults().size();
        int currentPage = state.getCurrentPage();
        int pageSize = state.getPageSize();
        
        return (currentPage + 1) * pageSize < totalResults;
    }
    
    /**
     * 获取分页信息
     */
    public PageInfo getPageInfo(String sessionId) {
        ConversationState state = getSession(sessionId);
        if (state == null || state.getAllResults() == null) {
            return null;
        }
        
        int totalResults = state.getAllResults().size();
        int currentPage = state.getCurrentPage();
        int pageSize = state.getPageSize();
        int totalPages = (int) Math.ceil((double) totalResults / pageSize);
        
        return new PageInfo(currentPage + 1, totalPages, totalResults, pageSize);
    }
    
    /**
     * 分页信息类
     */
    public static class PageInfo {
        private final int currentPage;  // 当前页（从1开始）
        private final int totalPages;   // 总页数
        private final int totalResults; // 总结果数
        private final int pageSize;     // 每页大小
        
        public PageInfo(int currentPage, int totalPages, int totalResults, int pageSize) {
            this.currentPage = currentPage;
            this.totalPages = totalPages;
            this.totalResults = totalResults;
            this.pageSize = pageSize;
        }
        
        public int getCurrentPage() { return currentPage; }
        public int getTotalPages() { return totalPages; }
        public int getTotalResults() { return totalResults; }
        public int getPageSize() { return pageSize; }
        public boolean hasNextPage() { return currentPage < totalPages; }
        public boolean hasPrevPage() { return currentPage > 1; }
    }
    
    /**
     * 更新筛选后的结果
     */
    public void updateFilteredResults(String sessionId, List<CircuitDocument> filteredResults) {
        ConversationState state = getSession(sessionId);
        if (state != null) {
            // 保存当前状态快照（在更新之前）
            state.saveStateSnapshot();
            
            state.setLastResults(filteredResults);
            state.setNarrowingStep(state.getNarrowingStep() + 1);
            state.updateActiveTime();
            
            log.debug("更新筛选结果 - SessionId: {}, 结果数: {}, 步骤: {}", 
                    sessionId, filteredResults.size(), state.getNarrowingStep());
        }
    }
    
    /**
     * 返回上一步
     */
    public boolean goBackToPreviousStep(String sessionId) {
        ConversationState state = getSession(sessionId);
        if (state != null && state.canGoBack()) {
            boolean success = state.goBack();
            if (success) {
                state.updateActiveTime();
                log.debug("返回上一步 - SessionId: {}, 当前结果数: {}, 步骤: {}", 
                        sessionId, 
                        state.getLastResults() != null ? state.getLastResults().size() : 0,
                        state.getNarrowingStep());
            }
            return success;
        }
        return false;
    }
    
    /**
     * 检查是否可以返回上一步
     */
    public boolean canGoBack(String sessionId) {
        ConversationState state = getSession(sessionId);
        return state != null && state.canGoBack();
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
        return message != null && (message.startsWith("category:") || message.startsWith("ai_category:"));
    }
    
    /**
     * 解析分类选择值
     */
    public String parseCategoryValue(String message) {
        if (message != null) {
            if (message.startsWith("ai_category:")) {
                return message.substring("ai_category:".length());
            } else if (message.startsWith("category:")) {
                return message.substring("category:".length());
            }
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
