package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.exception.NonRetryableException;
import com.yansheng.aiknowledgebase.exception.RetryExhaustedException;
import com.yansheng.aiknowledgebase.service.GenerationService;
import com.yansheng.aiknowledgebase.utils.HttpRetryUtil;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GenerationServiceImpl implements GenerationService {

    private static final int MAX_RETRIES = 3;

    private final OpenAiChatModel openAiChatModel;
    private final HttpRetryUtil httpRetryUtil;

    public GenerationServiceImpl(OpenAiChatModel openAiChatModel, HttpRetryUtil httpRetryUtil) {
        this.openAiChatModel = openAiChatModel;
        this.httpRetryUtil = httpRetryUtil;
    }

    @Override
    public String generate(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("Prompt不能为空");
        }
        try {
            return httpRetryUtil.executeWithRetry(() -> {
                ChatResponse response = openAiChatModel.call(new Prompt(prompt));
                return response.getResult().getOutput().getText();
            }, MAX_RETRIES);
        } catch (RetryExhaustedException | NonRetryableException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用DeepSeek生成回答失败", e);
        }
    }
}