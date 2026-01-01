package com.bo.chatbot.service;

import org.springframework.stereotype.Component;

/**
 * Prompt 构建器
 * 负责构建各种场景的 Prompt 模板
 */
@Component
public class PromptBuilder {
    
    /**
     * 构建查询理解 Prompt
     * 用于让 AI 理解用户的自然语言查询，提取结构化信息
     * 
     * @param userQuery 用户查询文本
     * @return Prompt 文本
     */
    public String buildQueryUnderstandingPrompt(String userQuery) {
        return String.format("""
                你是一个车辆电路图资料库的智能助手。你的任务是从用户的自然语言查询中提取关键信息。
                
                用户查询："%s"
                
                请从查询中提取以下信息，并以 JSON 格式返回（只返回 JSON，不要其他内容）：
                {
                  "brand": "品牌名称（如：三一、徐工、红岩、卡特、小松、日立、沃尔沃、康明斯等）",
                  "model": "型号（如：SY215、杰狮、320D、2000、M500等）",
                  "component": "部件类型（如：保险丝、仪表、ECU、液压、电脑板、显示器、玻璃升降器等）",
                  "ecuType": "ECU型号（如：CM2880、EDC7、DCM3.7、C81等）",
                  "queryType": "查询类型（整车电路图 或 ECU电路图）"
                }
                
                注意事项：
                1. 如果某项信息不确定或没有，填 null
                2. 品牌名称要标准化（如：小忪→小松，庆龄→庆铃，上气→上汽）
                3. 型号要提取数字和字母组合（如：2880、SY215、320D）
                4. 部件类型要提取核心词（如：保险丝图纸→保险丝，仪表针脚图→仪表）
                5. 只返回 JSON，不要解释
                
                示例：
                用户查询："红岩杰狮保险丝图纸"
                返回：{"brand":"红岩","model":"杰狮","component":"保险丝","ecuType":null,"queryType":"整车电路图"}
                
                用户查询："康明斯2880电路图"
                返回：{"brand":"康明斯","model":null,"component":null,"ecuType":"CM2880","queryType":"ECU电路图"}
                
                用户查询："小忪2ooo供电电路图"
                返回：{"brand":"小松","model":"2000","component":"供电","ecuType":null,"queryType":"整车电路图"}
                
                现在请处理用户查询并返回 JSON：
                """, userQuery);
    }
    
    /**
     * 构建澄清问题 Prompt
     * 当搜索结果较多时，生成引导用户的问题
     * 
     * @param userQuery 用户查询
     * @param resultCount 结果数量
     * @param brands 涉及的品牌列表
     * @return Prompt 文本
     */
    public String buildClarificationPrompt(String userQuery, int resultCount, String brands) {
        return String.format("""
                用户查询："%s"
                找到 %d 条相关资料，涉及品牌：%s
                
                请生成一个简洁、友好的引导问题，帮助用户缩小范围。
                要求：
                1. 一句话，不超过30字
                2. 语气友好自然
                3. 具体明确
                
                示例：
                "找到多条资料，请问您需要哪个品牌的？"
                "请问您需要哪个型号的电路图？"
                "找到多个部件，请问您需要哪一个？"
                
                请生成引导问题：
                """, userQuery, resultCount, brands);
    }
    
    /**
     * 构建无结果建议 Prompt
     * 当搜索无结果时，生成友好的建议
     * 
     * @param userQuery 用户查询
     * @return Prompt 文本
     */
    public String buildNoResultSuggestionPrompt(String userQuery) {
        return String.format("""
                用户查询："%s"
                未找到相关资料。
                
                请生成一个友好的回复，包含：
                1. 表示抱歉
                2. 2-3条具体的建议（如：检查品牌名称、使用更通用的关键词等）
                3. 给出1-2个查询示例
                
                要求：
                1. 语气友好
                2. 建议具体可行
                3. 不超过100字
                
                请生成回复：
                """, userQuery);
    }
}
