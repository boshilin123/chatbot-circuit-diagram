package com.bo.chatbot.service;

import com.google.gson.Gson;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek API 服务
 * 负责调用 DeepSeek API 进行自然语言理解
 */
@Slf4j
@Service
public class DeepSeekService {
    
    @Value("${deepseek.api.key}")
    private String apiKey;
    
    @Value("${deepseek.api.url}")
    private String apiUrl;
    
    private final HttpClient httpClient;
    private final Gson gson;
    
    public DeepSeekService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }
    
    /**
     * 调用 DeepSeek API
     * 
     * @param prompt 提示词
     * @return AI 返回的文本
     */
    public String callDeepSeek(String prompt) {
        return callDeepSeek(prompt, 0.7, 1000);
    }
    
    /**
     * 调用 DeepSeek API（完整参数）
     * 
     * @param prompt 提示词
     * @param temperature 温度参数（0-1，越高越随机）
     * @param maxTokens 最大返回 token 数
     * @return AI 返回的文本
     */
    public String callDeepSeek(String prompt, double temperature, int maxTokens) {
        try {
            log.debug("调用 DeepSeek API，Prompt 长度: {}", prompt.length());
            
            // 构建消息列表
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("user", prompt));
            
            // 构建请求体
            ChatRequest requestBody = new ChatRequest(
                    "deepseek-chat",
                    messages,
                    temperature,
                    maxTokens
            );
            
            String requestBodyJson = gson.toJson(requestBody);
            
            // 构建 HTTP 请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();
            
            // 发送请求
            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - startTime;
            
            log.debug("DeepSeek API 响应时间: {}ms, 状态码: {}", duration, response.statusCode());
            
            // 处理响应
            if (response.statusCode() == 200) {
                ChatResponse chatResponse = gson.fromJson(response.body(), ChatResponse.class);
                String content = chatResponse.getChoices().get(0).getMessage().getContent();
                log.debug("DeepSeek 返回内容长度: {}", content.length());
                return content;
            } else {
                log.error("DeepSeek API 调用失败，状态码: {}, 响应: {}", 
                        response.statusCode(), response.body());
                throw new RuntimeException("DeepSeek API 调用失败: " + response.statusCode());
            }
            
        } catch (Exception e) {
            log.error("调用 DeepSeek API 异常", e);
            throw new RuntimeException("DeepSeek API 调用异常: " + e.getMessage(), e);
        }
    }
    
    /**
     * 测试 API 连接
     */
    public boolean testConnection() {
        try {
            String response = callDeepSeek("你好", 0.7, 50);
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            log.error("DeepSeek API 连接测试失败", e);
            return false;
        }
    }
    
    // ========== 内部类：数据模型 ==========
    
    @Data
    static class Message {
        private String role;
        private String content;
        
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
    
    @Data
    static class ChatRequest {
        private String model;
        private List<Message> messages;
        private double temperature;
        private int max_tokens;
        
        public ChatRequest(String model, List<Message> messages, double temperature, int max_tokens) {
            this.model = model;
            this.messages = messages;
            this.temperature = temperature;
            this.max_tokens = max_tokens;
        }
    }
    
    @Data
    static class ChatResponse {
        private List<Choice> choices;
        
        @Data
        static class Choice {
            private Message message;
        }
    }
}
