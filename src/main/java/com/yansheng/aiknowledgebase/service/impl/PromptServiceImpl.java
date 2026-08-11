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

        return String.format("""
                【参考资料】
                %s

                【用户问题】
                %s
                """, contextBuilder.toString(), question);
    }
}
