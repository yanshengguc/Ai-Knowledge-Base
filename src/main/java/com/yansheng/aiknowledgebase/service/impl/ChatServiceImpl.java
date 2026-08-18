package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.ChatResponse;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.ChatService;
import com.yansheng.aiknowledgebase.service.ConversationHistoryService;
import com.yansheng.aiknowledgebase.service.GenerationService;
import com.yansheng.aiknowledgebase.service.PromptService;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
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
    public ChatResponse ask(Long userId, String question) {
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

        // 6. 返回回答 + 引用来源(供前端展示"回答出自哪些资料")
        return new ChatResponse(answer, searchResults);
    }

    @Override
    public void streamAsk(Long userId, String question,
                          java.util.function.Consumer<String> onToken,
                          java.util.function.Consumer<java.util.List<com.yansheng.aiknowledgebase.entity.SearchResult>> onDone) {
        // 1. 检索 + 历史 + Prompt(与 ask 相同)
        List<com.yansheng.aiknowledgebase.entity.SearchResult> searchResults = retrievalService.retrieveTopK(question);
        List<Map<String, String>> history = historyService.getHistory(userId);
        String prompt = promptService.buildChatPrompt(question, searchResults, history);

        // 2. 流式生成:逐 token 推给前端,收集完整答案
        StringBuilder fullAnswer = new StringBuilder();
        generationService.generateStream(prompt).subscribe(
                token -> {
                    fullAnswer.append(token);
                    onToken.accept(token);
                },
                error -> {
                    // 流中断:已收集部分保存到历史,避免整轮丢失
                    if (fullAnswer.length() > 0) {
                        historyService.append(userId, "assistant", fullAnswer.toString());
                    }
                    log.error("流式生成失败,userId={}", userId, error);
                },
                () -> {
                    // 3. 完成:保存本轮(user + assistant)到历史
                    historyService.append(userId, "user", question);
                    historyService.append(userId, "assistant", fullAnswer.toString());
                    onDone.accept(searchResults);
                }
        );
    }

    @Override
    public void clear(Long userId) {
        historyService.clear(userId);
    }

    @Override
    public java.util.List<java.util.Map<String, String>> history(Long userId) {
        return historyService.getHistory(userId);
    }
}
