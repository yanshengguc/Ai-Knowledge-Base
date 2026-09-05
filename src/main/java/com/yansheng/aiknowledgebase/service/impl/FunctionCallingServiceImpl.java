package com.yansheng.aiknowledgebase.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.common.tool.Tool;
import com.yansheng.aiknowledgebase.common.tool.ToolCallbackAdapter;
import com.yansheng.aiknowledgebase.common.tool.ToolRegistry;
import com.yansheng.aiknowledgebase.common.tool.ToolTraceSummarizer;
import com.yansheng.aiknowledgebase.entity.ToolTraceEvent;
import com.yansheng.aiknowledgebase.service.FunctionCallingService;
import com.yansheng.aiknowledgebase.service.TokenUsageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
public class FunctionCallingServiceImpl implements FunctionCallingService {

    /** 轨迹里参数/结果的展示截断:时间线只要"发生了什么",不要全文 */
    private static final int TRACE_MAX_LEN = 120;

    private final OpenAiChatModel openAiChatModel;
    private final TokenUsageService tokenUsageService;

    /**
     * Agent Loop 死循环防护:工具调用最多执行 maxSteps 轮,超限强制终止
     * (面试:死循环怎么掐 = 最大步数 + 进展校验 + 人工介入,这里是第一重)
     */
    private final int maxSteps;

    /**
     * 工具回调集合:工具在注册中心构造期已固定,这里构造时构建一次缓存,
     * 避免每次 execute() 重复适配(原先每次调用都重建)。
     */
    private final List<ToolCallback> toolCallbacks;

    public FunctionCallingServiceImpl(
            OpenAiChatModel openAiChatModel,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper,
            TokenUsageService tokenUsageService,
            @Value("${agent.max-steps:5}") int maxSteps) {

        this.openAiChatModel = openAiChatModel;
        this.tokenUsageService = tokenUsageService;
        this.maxSteps = maxSteps;

        List<ToolCallback> callbacks = new ArrayList<>();
        for (Tool tool : toolRegistry.getAllTools()) {
            // Schema 由每个工具自带(开闭原则:新增工具不改编排层)
            callbacks.add(new ToolCallbackAdapter(
                    tool,
                    objectMapper,
                    tool.getInputSchema()
            ));
        }
        this.toolCallbacks = List.copyOf(callbacks);
    }

    @Override
    public String execute(String prompt) {
        return execute(null, prompt, event -> { });
    }

    @Override
    public String execute(Long userId, String prompt, Consumer<ToolTraceEvent> onTool) {

        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("Prompt不能为空");
        }

        /*
         * 1. 工具回调已构造期缓存(工具集合运行期不变)
         * 2. 把 ToolCallback 配置到本次模型请求
         *    关闭框架的"自动工具执行",改为手动控制循环 —— 这样才能加 max_steps 死循环防护
         */
        ToolCallingChatOptions options =
                ToolCallingChatOptions.builder()
                        .toolCallbacks(toolCallbacks)
                        .internalToolExecutionEnabled(false)
                        .build();

        /*
         * 3. Agent Loop:模型 → 工具 → 观察 → 再决策
         *    (面试:这就是"循环工程"——自动化+工具+验证,外加 max_steps 死循环防护)
         */
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(prompt));

        int step = 0;
        String finalAnswer = null;
        while (step < maxSteps) {

            ChatResponse response = openAiChatModel.call(new Prompt(messages, options));
            recordUsage(userId, response);
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
                    // 工具轨迹回调:执行完再推,事件里带结果摘要(失败也有摘要,前端时间线如实展示)
                    // summary 由 ToolTraceSummarizer 按 toolName 翻译成人话,解析失败降级截断原文;
                    // 回传给模型的 ToolResponse 仍是原始 result,摘要只影响前端展示
                    onTool.accept(new ToolTraceEvent(
                            step + 1,
                            toolCall.name(),
                            truncate(toolCall.arguments()),
                            ToolTraceSummarizer.summarize(toolCall.name(), result)));
                }
                step++;
                continue;
            }

            // 模型不再调工具 = 最终答案
            finalAnswer = assistantMsg.getText();
            break;
        }

        /*
         * 4. 死循环防护:超限强制终止(不返回空,给用户明确提示)
         */
        if (finalAnswer == null) {
            finalAnswer = "任务已达到最大执行步数(" + maxSteps + "),已强制终止。建议拆分为更小的子问题重试。";
        }

        return finalAnswer;
    }

    /** Agent 模式的模型调用也记账:否则每次对话被拆成多轮 LLM 调用反而绕过了成本治理 */
    private void recordUsage(Long userId, ChatResponse response) {
        if (userId == null) {
            return;
        }
        try {
            Usage usage = response.getMetadata().getUsage();
            if (usage != null) {
                tokenUsageService.recordChat(userId,
                        usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                        usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
            }
        } catch (Exception e) {
            log.warn("Agent 模式 token 记账失败: {}", e.getMessage());
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() <= TRACE_MAX_LEN ? flat : flat.substring(0, TRACE_MAX_LEN) + "…";
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
                    // ToolCallbackAdapter 已包一层"工具执行失败: <工具名>"前缀,这里去重防文案叠罗汉
                    String msg = e.getMessage() == null ? name : e.getMessage();
                    String prefix = "工具执行失败: ";
                    String body = msg.startsWith(prefix) ? msg.substring(prefix.length()) : msg;
                    return prefix + body + "(请换一种方式处理或如实告知用户)";
                }
            }
        }
        return "工具不存在: " + name;
    }
}
