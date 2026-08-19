package com.yansheng.aiknowledgebase.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.common.tool.Tool;
import com.yansheng.aiknowledgebase.common.tool.ToolCallbackAdapter;
import com.yansheng.aiknowledgebase.common.tool.ToolRegistry;
import com.yansheng.aiknowledgebase.service.FunctionCallingService;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;


import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class FunctionCallingServiceImpl implements FunctionCallingService {

    private final OpenAiChatModel openAiChatModel;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    /**
     * file_trace 工具的参数 Schema
     */
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

    /**
     * file_search 工具的参数 Schema
     */
    private static final String FILE_SEARCH_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "用户的自然语言查询,原样传入,不要改写"
                }
              },
              "required": ["query"]
            }
            """;

    /**
     * 无参数工具的 Schema(knowledge_stats / time_now)
     */
    private static final String EMPTY_SCHEMA = """
            {
              "type": "object",
              "properties": {}
            }
            """;

    public FunctionCallingServiceImpl(
            OpenAiChatModel openAiChatModel,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper) {

        this.openAiChatModel = openAiChatModel;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(String prompt) {

        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("Prompt不能为空");
        }

        /*
         * 1. 获取注册中心里的所有工具
         */
        List<Tool> tools = toolRegistry.getAllTools();

        /*
         * 2. Tool → Spring AI ToolCallback
         */
        List<ToolCallback> toolCallbacks = new ArrayList<>();

        for (Tool tool : tools) {

            String inputSchema = getInputSchema(tool);

            ToolCallback callback =
                    new ToolCallbackAdapter(
                            tool,
                            objectMapper,
                            inputSchema
                    );

            toolCallbacks.add(callback);
        }

        /*
         * 3. 把 ToolCallback 配置到本次模型请求
         */
        ToolCallingChatOptions options =
                ToolCallingChatOptions.builder()
                        .toolCallbacks(toolCallbacks)
                        .build();

        /*
         * 4. 创建带工具配置的 Prompt
         */
        Prompt request = new Prompt(
                List.of(new UserMessage(prompt)),
                options
        );

        /*
         * 5. 调用模型
         */
        ChatResponse response =
                openAiChatModel.call(request);

        /*
         * 6. 返回最终答案
         */
        return response.getResult()
                .getOutput()
                .getText();
    }

    /**
     * 根据工具获取参数 Schema。
     *
     * 当前 Day39 只有 file_trace，
     * 所以暂时先写死。
     */
    private String getInputSchema(Tool tool) {

        if ("file_trace".equals(tool.getToolName())) {
            return FILE_TRACE_SCHEMA;
        }

        if ("file_search".equals(tool.getToolName())) {
            return FILE_SEARCH_SCHEMA;
        }

        if ("knowledge_stats".equals(tool.getToolName())
                || "time_now".equals(tool.getToolName())) {
            return EMPTY_SCHEMA;
        }

        throw new IllegalArgumentException(
                "未配置工具参数 Schema: "
                        + tool.getToolName()
        );
    }
}