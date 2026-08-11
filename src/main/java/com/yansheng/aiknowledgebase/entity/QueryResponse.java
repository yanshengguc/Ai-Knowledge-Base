package com.yansheng.aiknowledgebase.entity;

import java.util.List;

public class QueryResponse {

    private String answer;
    private List<SearchResult> sources;

    public QueryResponse(String answer, List<SearchResult> sources) {
        this.answer = answer;
        this.sources = sources;
    }

    public String getAnswer() {
        return answer;
    }

    public List<SearchResult> getSources() {
        return sources;
    }
}