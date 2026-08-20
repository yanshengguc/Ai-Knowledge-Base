package com.yansheng.aiknowledgebase.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.RerankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 博查 Semantic Reranker 实现。
 *
 * 降级策略(面试可讲):
 *   - 未配置 key / API 失败 / 超时 → 返回原顺序(粗排结果),检索链路不中断
 *   - 重排不是"必须",是"锦上添花"——降级保证主流程可用
 */
@Slf4j
@Service
public class RerankServiceImpl implements RerankService {

    private static final int HTTP_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * 重排 API 配置(可切换 provider):
     *   默认 硅基流动(SiliconFlow,国内直连,免费模型 BAAI/bge-reranker-v2-m3)
     *   备选 博查 BoCha(gte-rerank,需余额)
     */
    @Value("${rerank.endpoint:https://api.siliconflow.cn/v1/rerank}")
    private String endpoint;

    @Value("${rerank.model:BAAI/bge-reranker-v2-m3}")
    private String model;

    @Value("${rerank.api-key:}")
    private String apiKey;

    public RerankServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public List<SearchResult> rerank(String query, List<SearchResult> candidates, int topN) {
        // 降级①:未配置 key 或候选太少,直接截取返回
        if (apiKey == null || apiKey.isBlank() || candidates == null || candidates.size() <= 1) {
            return truncate(candidates, topN);
        }

        try {
            List<String> documents = new ArrayList<>();
            for (SearchResult r : candidates) {
                documents.add(r.getContent() == null ? "" : r.getContent());
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", topN);
            body.put("return_documents", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("重排 API 非200: status={}, provider={}", response.statusCode(), endpoint);
                return truncate(candidates, topN);
            }

            return reorderByScore(candidates, response.body(), topN);
        } catch (Exception e) {
            // 降级②:重排失败不影响主流程,返回原顺序
            log.warn("重排失败,降级返回粗排结果: {}", e.getMessage());
            return truncate(candidates, topN);
        }
    }

    /** 按重排 relevance_score 降序重排候选,取 topN */
    private List<SearchResult> reorderByScore(List<SearchResult> candidates, String rawJson, int topN) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);
        // 兼容两种响应格式:硅基流动 results 在顶层;博查在 data.results
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            results = root.path("data").path("results");
        }

        // index -> relevance_score
        Map<Integer, Double> scoreMap = new LinkedHashMap<>();
        if (results.isArray()) {
            for (JsonNode item : results) {
                scoreMap.put(item.path("index").asInt(), item.path("relevance_score").asDouble());
            }
        }

        List<SearchResult> reordered = new ArrayList<>(candidates);
        // 按重排分数降序(未出现的 index 排最后,保留原相对顺序)
        reordered.sort((a, b) -> {
            double sa = scoreMap.getOrDefault(candidates.indexOf(a), -1.0);
            double sb = scoreMap.getOrDefault(candidates.indexOf(b), -1.0);
            return Double.compare(sb, sa);
        });
        log.info("重排完成: {} 候选 → 前 {} 个", candidates.size(), Math.min(topN, reordered.size()));
        return truncate(reordered, topN);
    }

    private List<SearchResult> truncate(List<SearchResult> list, int n) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list.subList(0, Math.min(n, list.size())));
    }
}
