package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.RerankService;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class RetrievalServiceImpl implements RetrievalService {

    private final VectorSearchService vectorSearchService;
    private final RerankService rerankService;

    @Value("${retrieval.top-k}")
    private int topK;

    @Value("${retrieval.similarity-threshold}")
    private double similarityThreshold;

    @Value("${retrieval.rerank.enabled:true}")
    private boolean rerankEnabled;

    public RetrievalServiceImpl(VectorSearchService vectorSearchService, RerankService rerankService) {
        this.vectorSearchService = vectorSearchService;
        this.rerankService = rerankService;
    }

    @Override
    public List<SearchResult> retrieveTopK(String queryText) {
        // 1. 粗召回(向量检索,多召回一些留给重排)
        List<SearchResult> rawResults = vectorSearchService.search(queryText, topK * 3);

        // 2. 阈值过滤(去掉明显不相关的噪声邻居)
        List<SearchResult> filtered = rawResults.stream()
                .filter(r -> r.getScore() <= similarityThreshold)
                .collect(Collectors.toList());

        // 3. 重排(精排):粗召回 → 交叉编码器重打分 → 取 topK;失败降级按原分排序
        if (rerankEnabled && !filtered.isEmpty()) {
            return rerankService.rerank(queryText, filtered, topK);
        }

        return filtered.stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore))
                .limit(topK)
                .collect(Collectors.toList());
    }


}