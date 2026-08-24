package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.ChatResponse;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.ChatService;
import com.yansheng.aiknowledgebase.service.ConversationHistoryService;
import com.yansheng.aiknowledgebase.service.LongTermMemoryService;
import com.yansheng.aiknowledgebase.service.GenerationService;
import com.yansheng.aiknowledgebase.service.PromptService;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import com.yansheng.aiknowledgebase.service.TokenUsageService;
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
    private final WebSearchTool webSearchTool;
    private final LongTermMemoryService longTermMemoryService;
    private final TokenUsageService tokenUsageService;

    public ChatServiceImpl(RetrievalService retrievalService,
                           PromptService promptService,
                           GenerationService generationService,
                           ConversationHistoryService historyService,
                           WebSearchTool webSearchTool,
                           LongTermMemoryService longTermMemoryService,
                           TokenUsageService tokenUsageService) {
        this.retrievalService = retrievalService;
        this.promptService = promptService;
        this.generationService = generationService;
        this.historyService = historyService;
        this.webSearchTool = webSearchTool;
        this.longTermMemoryService = longTermMemoryService;
        this.tokenUsageService = tokenUsageService;
    }

    @Override
    public ChatResponse ask(Long userId, String question) {
        return ask(userId, question, false);
    }

    @Override
    public ChatResponse ask(Long userId, String question, boolean enableWebSearch) {
        // 1. 知识库检索(当前问题)
        List<SearchResult> searchResults = retrievalService.retrieveTopK(question);

        // 2. 读取会话历史(动态上下文)
        List<Map<String, String>> history = historyService.getHistory(userId);

        // 3. 拼接带历史的 Prompt(可选:附加联网搜索结果,用户显式授权)
        String prompt = promptService.buildChatPrompt(question, searchResults, history);
        if (enableWebSearch) {
            prompt = appendWebSearchContext(prompt, question);
        }

        // 4. 生成回答(回调记录 token 用量)
        String answer = generationService.generate(prompt,
                (p, c) -> tokenUsageService.recordChat(userId, p, c));

        // 5. 保存本轮对话到历史 + 写入长期记忆(问题+回答摘要,供跨会话召回)
        historyService.append(userId, "user", question);
        historyService.append(userId, "assistant", answer);
        String memoryContent = "用户问过：" + question + "\n回答要点：" +
                (answer.length() > 100 ? answer.substring(0, 100) : answer);
        longTermMemoryService.remember(userId, memoryContent);

        // 6. 返回回答 + 引用来源(供前端展示"回答出自哪些资料")
        return new ChatResponse(answer, searchResults);
    }

    /**
     * 联网搜索并附加到 Prompt(用户显式开启"🌐联网"开关才调用)。
     * 容错:搜索失败不阻塞主流程,降级为纯知识库回答。
     */
    private String appendWebSearchContext(String prompt, String question) {
        try {
            String webContext = webSearchTool.execute(java.util.Map.of("query", question));
            log.info("联网搜索完成,注入上下文,question={}", question);
            return prompt + "\n\n【联网搜索结果】\n" + webContext
                    + "\n请结合以上最新联网信息回答;若与知识库内容冲突,以联网搜索结果为准。"
                    + "\n不要在回答中生成[来源:xxx]之类的引用标记,直接给出内容即可。";
        } catch (Exception e) {
            // 搜索失败降级:纯知识库回答,不中断对话
            log.warn("联网搜索失败,降级为纯知识库回答: {}", e.getMessage());
            return prompt;
        }
    }

    @Override
    public void streamAsk(Long userId, String question,
                          java.util.function.Consumer<String> onToken,
                          java.util.function.Consumer<java.util.List<com.yansheng.aiknowledgebase.entity.SearchResult>> onDone) {
        streamAsk(userId, question, onToken, onDone, false);
    }

    @Override
    public void streamAsk(Long userId, String question,
                          java.util.function.Consumer<String> onToken,
                          java.util.function.Consumer<java.util.List<com.yansheng.aiknowledgebase.entity.SearchResult>> onDone,
                          boolean enableWebSearch) {
        // 1. 检索 + 历史 + 长期记忆 + Prompt(与 ask 相同)
        List<com.yansheng.aiknowledgebase.entity.SearchResult> searchResults = retrievalService.retrieveTopK(question);
        List<Map<String, String>> history = historyService.getHistory(userId);
        List<String> memories = longTermMemoryService.recall(userId, question, 3);
        String prompt = promptService.buildChatPrompt(question, searchResults, history, memories);
        if (enableWebSearch) {
            prompt = appendWebSearchContext(prompt, question);
        }

        // 2. 先落 user 消息再开始生成:流中断时历史不会出现"有答无问"
        historyService.append(userId, "user", question);

        // 3. 流式生成:逐 token 推给前端,收集完整答案(流结束回调记录 token 用量)
        StringBuilder fullAnswer = new StringBuilder();
        generationService.generateStream(prompt,
                        (p, c) -> tokenUsageService.recordChat(userId, p, c))
                .subscribe(
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
                    // 失败也要发结束信号:onDone 负责 complete SSE,不发前端会挂到超时
                    onDone.accept(searchResults);
                },
                () -> {
                    // 4. 完成:保存 assistant 到历史 + 写入长期记忆
                    historyService.append(userId, "assistant", fullAnswer.toString());
                    String memoryContent = "用户问过：" + question + "\n回答要点：" +
                            (fullAnswer.length() > 100 ? fullAnswer.substring(0, 100) : fullAnswer.toString());
                    longTermMemoryService.remember(userId, memoryContent);
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
