package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.exception.NonRetryableException;
import com.yansheng.aiknowledgebase.exception.RetryExhaustedException;
import com.yansheng.aiknowledgebase.service.GenerationService;
import com.yansheng.aiknowledgebase.utils.HttpRetryUtil;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class GenerationServiceImpl implements GenerationService {

    private static final int MAX_RETRIES = 3;

    private final OpenAiChatModel openAiChatModel;
    private final HttpRetryUtil httpRetryUtil;
    private static final String SYSTEM_PROMPT = """
        你是一个基于知识库回答问题的助手。
        请严格遵守以下规则：
        1. 只根据【参考资料】中的内容回答问题，不要使用你自己的通用知识编造答案。
        2. 如果【参考资料】显示"未检索到相关资料"，或者资料内容与用户问题明显不相关，
           必须明确告知用户"知识库中未找到相关信息"，不要强行给出答案。
        3. 回答中引用资料时，使用格式"[来源：资料X]"标注具体引用了哪条资料。
        4. 保持回答简洁、准确，不要过度发散。
        """;
    public GenerationServiceImpl(OpenAiChatModel openAiChatModel, HttpRetryUtil httpRetryUtil) {
        this.openAiChatModel = openAiChatModel;
        this.httpRetryUtil = httpRetryUtil;
    }


    @Override
    public reactor.core.publisher.Flux<String> generateStream(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("Prompt不能为空");
        }
        Prompt fullPrompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(prompt)
        ));
        // 流式调用(不做同步重试:流式重试语义复杂,连接中断由前端重发兜底)
        // 注意:流式响应末尾可能包含空 chunk/usage chunk(result 或 text 为 null),需判空过滤
        return openAiChatModel.stream(fullPrompt)
                .map(response -> {
                    var result = response.getResult();
                    if (result == null || result.getOutput() == null || result.getOutput().getText() == null) {
                        return "";
                    }
                    return result.getOutput().getText();
                })
                .filter(text -> !text.isEmpty());
    }

    @Override
    public String generate(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("Prompt不能为空");
        }
        try {
            return httpRetryUtil.executeWithRetry(() -> {
                Prompt fullPrompt = new Prompt(List.of(
                        new SystemMessage(SYSTEM_PROMPT),
                        new UserMessage(prompt)
                ));
                ChatResponse response = openAiChatModel.call(fullPrompt);
                return response.getResult().getOutput().getText();
            }, MAX_RETRIES);
        } catch (RetryExhaustedException | NonRetryableException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用DeepSeek生成回答失败", e);
        }
    }
}