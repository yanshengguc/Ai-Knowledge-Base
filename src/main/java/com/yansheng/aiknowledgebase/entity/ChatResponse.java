package com.yansheng.aiknowledgebase.entity;

import java.util.List;

/**
 * 聊天响应:回答 + 引用来源(供前端展示"回答出自哪些资料")
 */
public class ChatResponse {
    private String answer;
    private List<SearchResult> references;

    public ChatResponse() {
    }

    public ChatResponse(String answer, List<SearchResult> references) {
        this.answer = answer;
        this.references = references;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<SearchResult> getReferences() {
        return references;
    }

    public void setReferences(List<SearchResult> references) {
        this.references = references;
    }
}
