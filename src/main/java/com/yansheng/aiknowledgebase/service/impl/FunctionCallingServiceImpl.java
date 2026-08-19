package com.yansheng.aiknowledgebase.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.common.tool.Tool;
import com.yansheng.aiknowledgebase.common.tool.ToolCallbackAdapter;
import com.yansheng.aiknowledgebase.common.tool.ToolRegistry;
import com.yansheng.aiknowledgebase.service.FunctionCallingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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

@Slf4j
@Service
public class FunctionCallingServiceImpl implements FunctionCallingService {

    /**
     * Agent Loop 死循环防护:工具调用最多执行 MAX_STEPS 轮,超限强制终止
     * (面试:死循环怎么掐 = 最大步数 + 进展校验 + 人工介入,这里是第一重)
     */
    private static final int MAX_STEPS = 5;

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

    /**
     * web_search 工具的参数 Schema(query 必填,count 可选)
     */
    private static final String WEB_SEARCH_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "用户想要联网搜索的内容,原样传入,不要改写"
                },
                "count": {
                  "type": "integer",
                  "description": "返回结果数,可选,默认5"
                }
              },
              "required": ["query"]
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
         *    关闭框架的"自动工具执行",改为手动控制循环 —— 这样才能加 max_steps 死循环防护
         */
        ToolCallingChatOptions options =
                ToolCallingChatOptions.builder()
                        .toolCallbacks(toolCallbacks)
                        .internalToolExecutionEnabled(false)
                        .build();

        /*
         * 4. Agent Loop:模型 → 工具 → 观察 → 再决策
         *    (面试:这就是"循环工程"——自动化+工具+验证,外加 max_steps 死循环防护)
         */
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(prompt));

        int step = 0;
        String finalAnswer = null;
        while (step < MAX_STEPS) {

            ChatResponse response = openAiChatModel.call(new Prompt(messages, options));
            AssistantMessage assistantMsg = response.getResult().getOutput();
            messages.add(assistantMsg);

            // 模型要调工具 → 逐个执行,结果回填对话,进入下一轮
            if (assistantMsg.hasToolCalls()) {
                log.info("Agent Loop 第 {} 轮:模型调用 {} 个工具", step + 1, assistantMsg.getToolCalls().size());
                for (AssistantMessage.ToolCall toolCall : assistantMsg.getToolCalls()) {
                    String result = executeTool(toolCall, toolCallbacks);
                    messages.add(ToolResponseMessage.builder()
                            .responses(List.of(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), result)))
                            .build());
                }
                step++;
                continue;
            }

            // 模型不再调工具 = 最终答案
            finalAnswer = assistantMsg.getText();
            break;
        }

        /*
         * 5. 死循环防护:超限强制终止(不返回空,给用户明确提示)
         */
        if (finalAnswer == null) {
            finalAnswer = "任务已达到最大执行步数(" + MAX_STEPS + "),已强制终止。建议拆分为更小的子问题重试。";
        }

        return finalAnswer;
    }

    /**
     * 执行单个工具调用(按名字匹配回调)。
     * 失败时把错误信息回传模型 —— 让模型"自愈"(换个参数或换工具),这是 loop 的容错。
     */
    private String executeTool(AssistantMessage.ToolCall toolCall, List<ToolCallback> callbacks) {
        String name = toolCall.name();
        for (ToolCallback cb : callbacks) {
            if (cb.getToolDefinition().name().equals(name)) {
                try {
                    return cb.call(toolCall.arguments());
                } catch (Exception e) {
                    log.warn("工具执行失败: {}, 错误: {}", name, e.getMessage());
                    return "工具执行失败: " + e.getMessage() + "(请换一种方式处理或如实告知用户)";
                }
            }
        }
        return "工具不存在: " + name;
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

        if ("web_search".equals(tool.getToolName())) {
            return WEB_SEARCH_SCHEMA;
        }

        throw new IllegalArgumentException(
                "未配置工具参数 Schema: "
                        + tool.getToolName()
        );
    }
}