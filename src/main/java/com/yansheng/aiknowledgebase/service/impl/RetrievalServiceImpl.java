package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.SearchResult;
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

    @Value("${retrieval.top-k}")
    private int topK;

    @Value("${retrieval.similarity-threshold}")
    private double similarityThreshold;

    public RetrievalServiceImpl(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @Override
    public List<SearchResult> retrieveTopK(String queryText) {
        List<SearchResult> rawResults = vectorSearchService.search(queryText, topK * 3);
        return rawResults.stream()
                .filter(r -> r.getScore() <= similarityThreshold)
                .sorted(Comparator.comparingDouble(SearchResult::getScore))
                .limit(topK)
                .collect(Collectors.toList());
    }


}