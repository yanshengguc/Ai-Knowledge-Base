package com.yansheng.aiknowledgebase.service;

/**
 * 多轮对话服务:会话记忆(动态上下文)+ RAG 问答
 */
public interface ChatService {

    /**
     * 多轮问答:结合会话历史 + 知识库检索,返回回答
     *
     * @param userId   当前用户(会话隔离)
     * @param question 用户问题
     */
    String ask(Long userId, String question);
}
