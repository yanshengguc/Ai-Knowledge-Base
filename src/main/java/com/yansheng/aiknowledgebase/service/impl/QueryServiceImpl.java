package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.QueryResponse;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.GenerationService;
import com.yansheng.aiknowledgebase.service.PromptService;
import com.yansheng.aiknowledgebase.service.QueryService;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QueryServiceImpl implements QueryService {

    private final RetrievalService retrievalService;
    private final PromptService promptService;
    private final GenerationService generationService;

    public QueryServiceImpl(RetrievalService retrievalService, PromptService promptService, GenerationService generationService) {
        this.retrievalService = retrievalService;
        this.promptService = promptService;
        this.generationService = generationService;
    }

    // 构造器注入

    @Override
    public QueryResponse query(String userQuestion) {
        // 第一步：调用retrievalService，传入userQuestion，拿到List<SearchResult>
        List<SearchResult> searchResults = retrievalService.retrieveTopK(userQuestion);
        // 第二步：调用promptService，传入userQuestion和上一步结果，拿到拼接好的prompt
     String  prompt= promptService.buildPrompt(userQuestion,searchResults);
        // 第三步：调用generationService，传入prompt，拿到最终回答
      return  new  QueryResponse(generationService.generate(prompt), searchResults) ;
        // return 最终回答
    }
}