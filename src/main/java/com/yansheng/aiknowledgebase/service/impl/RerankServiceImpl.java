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

    /**
     * 重排分数下限(0~1,relevance_score):低于阈值视为不相关,淘汰不进上下文。
     * 防"数量截断混入噪声"——topK 凑不满没关系,宁缺毋滥;配置可调,置 0 关闭淘汰。
     */
    @Value("${rerank.min-score:0.3}")
    private double minScore;

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
            // 拿全量候选的重排分(而非只回 topN),本地先按分数下限淘汰再截断——
            // 否则低分噪声在 API 侧已占满 topN 名额,淘汰无从谈起
            body.put("top_n", candidates.size());
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

    /** 按重排 relevance_score 降序重排候选,分数下限淘汰,取 topN(包级可见,供分数淘汰单测) */
    List<SearchResult> reorderByScore(List<SearchResult> candidates, String rawJson, int topN) throws Exception {
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

        // 一个分数都没拿到 = 响应结构异常(非预期格式/空 results)——视为本次重排失败,
        // 降级返回粗排原顺序(与 非200/超时 一致),不能当作"全部低于阈值"误淘汰
        if (scoreMap.isEmpty()) {
            log.warn("重排响应未含任何分数,降级返回粗排结果: provider={}", endpoint);
            return truncate(candidates, topN);
        }

        // 按重排分数降序(未出现的 index 排最后,保留原相对顺序)。
        // 对下标数组排序,comparator 内直接查 Map;原实现每次比较都 List.indexOf 是 O(n²)
        Integer[] order = new Integer[candidates.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (ia, ib) ->
                Double.compare(scoreMap.getOrDefault(ib, -1.0), scoreMap.getOrDefault(ia, -1.0)));

        // 分数下限淘汰:重排分低于阈值(含未拿到分数的)视为不相关,不进上下文;
        // 全部淘汰 = 该问题在知识库里没有相关资料,返回空列表让上层如实作答,不硬凑 topK
        List<SearchResult> reordered = new ArrayList<>(candidates.size());
        int dropped = 0;
        for (Integer idx : order) {
            if (scoreMap.getOrDefault(idx, -1.0) < minScore) {
                dropped++;
                continue;
            }
            reordered.add(candidates.get(idx));
        }
        if (dropped > 0) {
            log.info("重排分数下限 {} 淘汰 {} 条低分候选,保留 {} 条", minScore, dropped, reordered.size());
        }
        return truncate(reordered, topN);
    }

    private List<SearchResult> truncate(List<SearchResult> list, int n) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list.subList(0, Math.min(n, list.size())));
    }
}
