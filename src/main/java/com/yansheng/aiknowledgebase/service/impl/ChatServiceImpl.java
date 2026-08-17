package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.ChatService;
import com.yansheng.aiknowledgebase.service.ConversationHistoryService;
import com.yansheng.aiknowledgebase.service.GenerationService;
import com.yansheng.aiknowledgebase.service.PromptService;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChatServiceImpl implements ChatService {

    private final RetrievalService retrievalService;
    private final PromptService promptService;
    private final GenerationService generationService;
    private final ConversationHistoryService historyService;

    public ChatServiceImpl(RetrievalService retrievalService,
                           PromptService promptService,
                           GenerationService generationService,
                           ConversationHistoryService historyService) {
        this.retrievalService = retrievalService;
        this.promptService = promptService;
        this.generationService = generationService;
        this.historyService = historyService;
    }

    @Override
    public String ask(Long userId, String question) {
        // 1. 知识库检索(当前问题)
        List<SearchResult> searchResults = retrievalService.retrieveTopK(question);

        // 2. 读取会话历史(动态上下文)
        List<Map<String, String>> history = historyService.getHistory(userId);

        // 3. 拼接带历史的 Prompt
        String prompt = promptService.buildChatPrompt(question, searchResults, history);

        // 4. 生成回答
        String answer = generationService.generate(prompt);

        // 5. 保存本轮对话到历史
        historyService.append(userId, "user", question);
        historyService.append(userId, "assistant", answer);

        return answer;
    }
}
