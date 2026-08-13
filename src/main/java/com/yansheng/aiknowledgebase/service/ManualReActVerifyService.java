package com.yansheng.aiknowledgebase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.common.tool.Tool;
import com.yansheng.aiknowledgebase.common.tool.ToolCallbackAdapter;
import com.yansheng.aiknowledgebase.common.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
@Slf4j
@Service
public class ManualReActVerifyService {

    private final OpenAiChatModel openAiChatModel;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final ToolCallingManager toolCallingManager;

    private static final int MAX_ITERATIONS = 5;

    private static final String FILE_TRACE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "fileId": {
                  "type": "integer",
                  "description": "文件ID"
                }
              },
              "required": ["fileId"]
            }
            """;

    public ManualReActVerifyService(
            OpenAiChatModel openAiChatModel,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper) {

        this.openAiChatModel = openAiChatModel;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        // ToolCallingManager 手动 new 一个,不依赖 Spring 自动注入
        this.toolCallingManager = ToolCallingManager.builder().build();
    }

    public String executeManually(String userPrompt) {

        List<Tool> tools = toolRegistry.getAllTools();
        List<ToolCallback> toolCallbacks = new ArrayList<>();

        for (Tool tool : tools) {
            toolCallbacks.add(new ToolCallbackAdapter(
                    tool, objectMapper, FILE_TRACE_SCHEMA));
        }

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false) // 关键:关掉自动模式
                .build();

        Prompt prompt = new Prompt(userPrompt, chatOptions);
        ChatResponse chatResponse = openAiChatModel.call(prompt);

        int round = 0;
        while (chatResponse.hasToolCalls() && round < MAX_ITERATIONS) {
            round++;
            log.info(">>> 手动循环第{}轮:检测到ToolCall,准备执行", round);

            // 这一行帮你做了"找工具+解析参数+执行"整套逻辑
            List<Message> messages =
                    toolCallingManager.executeToolCalls(prompt, chatResponse).conversationHistory();

            prompt = new Prompt(messages, chatOptions);
            chatResponse = openAiChatModel.call(prompt);
        }

        log.info(">>> 手动循环结束,共执行{}轮", round);
        return chatResponse.getResult().getOutput().getText();
    }
}