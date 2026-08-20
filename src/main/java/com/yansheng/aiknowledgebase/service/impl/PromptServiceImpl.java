package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.PromptService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptServiceImpl implements PromptService {

    private static final int MAX_CONTEXT_LENGTH = 3000; // TODO: 简单字符数上限，后续用真实token计数库精细化

    @Override
    public String buildPrompt(String question, List<SearchResult> retrievedResults) {
        return buildChatPrompt(question, retrievedResults, List.of());
    }

    @Override
    public String buildChatPrompt(String question, List<SearchResult> retrievedResults,
                                  List<java.util.Map<String, String>> history) {
        return buildChatPrompt(question, retrievedResults, history, List.of());
    }

    @Override
    public String buildChatPrompt(String question, List<SearchResult> retrievedResults,
                                  List<java.util.Map<String, String>> history,
                                  List<String> memories) {
        StringBuilder contextBuilder = new StringBuilder();

        if (retrievedResults == null || retrievedResults.isEmpty()) {
            contextBuilder.append("（未检索到相关资料）");
        } else {
            int currentLength = 0;
            for (int i = 0; i < retrievedResults.size(); i++) {
                String cleanedContent = retrievedResults.get(i).getContent().trim();
                String segment = String.format("资料%d：%s%n", i + 1, cleanedContent);

                if (currentLength + segment.length() > MAX_CONTEXT_LENGTH) {
                    break; // 超出上限就停止拼接，保留前面更相关的内容（已按score排序）
                }
                contextBuilder.append(segment);
                currentLength += segment.length();
            }
        }

        // 会话历史(动态上下文):非空时拼入,让模型能引用前文;空则省略该段
        boolean hasHistory = history != null && !history.isEmpty();
        StringBuilder historyBuilder = new StringBuilder();
        if (hasHistory) {
            for (java.util.Map<String, String> msg : history) {
                String role = "user".equals(msg.get("role")) ? "用户" : "助手";
                historyBuilder.append(role).append("：").append(msg.get("content")).append("\n");
            }
        }

        // 长期记忆(跨会话):非空时拼入,让模型记得用户此前的偏好/结论
        boolean hasMemories = memories != null && !memories.isEmpty();
        StringBuilder memoryBuilder = new StringBuilder();
        if (hasMemories) {
            for (int i = 0; i < memories.size(); i++) {
                memoryBuilder.append("记忆").append(i + 1).append("：").append(memories.get(i)).append("\n");
            }
        }

        if (!hasHistory) {
            if (!hasMemories) {
                return String.format("""
                        【参考资料】
                        %s

                        【用户问题】
                        %s
                        请基于参考资料回答；资料中没有的内容如实说明，不要编造。
                        """, contextBuilder.toString(), question);
            }
            return String.format("""
                    【长期记忆】
                    %s
                    【参考资料】
                    %s

                    【用户问题】
                    %s
                    请结合长期记忆和参考资料回答；资料中没有的内容如实说明，不要编造。
                    """, memoryBuilder.toString(), contextBuilder.toString(), question);
        }
        if (!hasMemories) {
            return String.format("""
                    【对话历史】
                    %s
                    【参考资料】
                    %s

                    【用户问题】
                    %s
                    请基于对话历史和参考资料回答；资料中没有的内容如实说明，不要编造。
                    """, historyBuilder.toString(), contextBuilder.toString(), question);
        }
        return String.format("""
                【对话历史】
                %s
                【长期记忆】
                %s
                【参考资料】
                %s

                【用户问题】
                %s
                请结合对话历史和长期记忆回答；资料中没有的内容如实说明，不要编造。
                """, historyBuilder.toString(), memoryBuilder.toString(), contextBuilder.toString(), question);
    }
}
