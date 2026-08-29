package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.ChatResponse;

/**
 * 多轮对话服务:会话记忆(动态上下文)+ RAG 问答
 */
public interface ChatService {

    /**
     * 多轮问答:结合会话历史 + 知识库检索,返回回答与引用来源
     *
     * @param userId   当前用户(会话隔离)
     * @param question 用户问题
     */
    ChatResponse ask(Long userId, String question);

    /**
     * 多轮问答(支持联网搜索)
     *
     * @param enableWebSearch true = 回答前先联网搜索,最新信息注入上下文(用户显式授权)
     */
    ChatResponse ask(Long userId, String question, boolean enableWebSearch);

    /** 清空当前用户会话历史 */
    void clear(Long userId);

    /** 读取当前用户会话历史(前端刷新后恢复) */
    java.util.List<java.util.Map<String, String>> history(Long userId);

    /**
     * 流式多轮问答(SSE 打字机效果)
     * @param onToken 每个 token 回调(线程安全,直接推给前端)
     * @param onDone  生成完成回调(携带引用来源,此时已写入会话历史)
     */
    void streamAsk(Long userId, String question,
                   java.util.function.Consumer<String> onToken,
                   java.util.function.Consumer<java.util.List<com.yansheng.aiknowledgebase.entity.SearchResult>> onDone);

    /**
     * 流式多轮问答(支持联网搜索)
     * @param enableWebSearch true = 回答前先联网搜索,最新信息注入上下文(用户显式授权)
     */
    void streamAsk(Long userId, String question,
                   java.util.function.Consumer<String> onToken,
                   java.util.function.Consumer<java.util.List<com.yansheng.aiknowledgebase.entity.SearchResult>> onDone,
                   boolean enableWebSearch);

    /**
     * Agent 模式流式问答:走手写 ReAct 循环(模型自主决策调用工具),工具轨迹经 onTool 推给前端时间线。
     * 与快速路径相比多几次模型调用(成本更高、延迟更长),所以做成用户显式开关而非默认。
     */
    void streamAskWithAgent(Long userId, String question,
                            java.util.function.Consumer<String> onToken,
                            java.util.function.Consumer<com.yansheng.aiknowledgebase.entity.ToolTraceEvent> onTool,
                            java.util.function.Consumer<java.util.List<com.yansheng.aiknowledgebase.entity.SearchResult>> onDone);
}
