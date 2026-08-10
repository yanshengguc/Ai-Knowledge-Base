package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.common.SearchResult;

import java.util.List;

public interface PromptService {
    String buildPrompt(String question, List<SearchResult> retrievedResults);
}
