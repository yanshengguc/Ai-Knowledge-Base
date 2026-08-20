package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.SearchResult;

import java.util.List;
import java.util.Map;

public interface PromptService {
    String buildPrompt(String question, List<SearchResult> retrievedResults);

    /** 多轮版:在单轮基础上增加会话历史上下文 */
    String buildChatPrompt(String question, List<SearchResult> retrievedResults, List<Map<String, String>> history);

    /** 多轮版 + 长期记忆:在会话历史基础上增加跨会话的长期记忆上下文 */
    String buildChatPrompt(String question, List<SearchResult> retrievedResults,
                           List<Map<String, String>> history, List<String> memories);
}
