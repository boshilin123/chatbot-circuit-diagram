package com.bo.chatbot.config;

import com.bo.chatbot.service.RateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 限流拦截器
 * 在请求处理前进行限流检查
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只对API接口进行限流
        String requestURI = request.getRequestURI();
        if (!requestURI.startsWith("/api/")) {
            return true;
        }
        
        // 获取客户端IP和会话ID
        String clientIp = getClientIp(request);
        String sessionId = request.getParameter("sessionId");
        if (sessionId == null) {
            // 从请求体中获取sessionId（需要特殊处理）
            sessionId = "unknown";
        }
        
        // 进行限流检查
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(clientIp, sessionId);
        
        if (!result.isAllowed()) {
            log.warn("请求被拦截器限流 - URI: {}, IP: {}, Reason: {}", 
                    requestURI, clientIp, result.getReason());
            
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"%s\",\"code\":429}", 
                result.getMessage()));
            return false;
        }
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                              Object handler, Exception ex) throws Exception {
        // 请求完成后减少并发计数
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/api/")) {
            rateLimitService.requestCompleted();
        }
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 处理多个IP的情况，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip != null ? ip : "unknown";
    }
}