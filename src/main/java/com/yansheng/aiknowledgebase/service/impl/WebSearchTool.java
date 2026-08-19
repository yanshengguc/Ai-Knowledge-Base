package com.yansheng.aiknowledgebase.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.common.tool.Tool;
import com.yansheng.aiknowledgebase.exception.BusinessException;
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
 * web_search 工具:联网搜索(博查 BoCha API,国内 AI 搜索,免代理)。
 *
 * 用户触发式设计(面试可讲):
 *   1. 描述强约束"仅当用户明确要求联网搜索时才调用"——模型不主动搜,
 *      避免每轮问答都拖慢响应 + 烧搜索额度(成本控制)
 *   2. 前端"🌐联网"开关(enableWebSearch)控制是否允许注入本工具 = 用户显式授权
 *   3. 结果精简返回(name/url/summary),整页内容不进上下文,防稀释模型注意力
 *      (与 file_search 的轻量返回同一原则)
 *   4. 异常兜底:未配置 key / API 失败 → 明确报错,不吞不炸
 */
@Slf4j
@Service
public class WebSearchTool implements Tool {

    private static final String BOCHA_ENDPOINT = "https://api.bochaai.com/v1/web-search";
    private static final int HTTP_TIMEOUT_SECONDS = 12;
    private static final int SUMMARY_MAX = 150;
    private static final int DEFAULT_COUNT = 5;
    private static final int MAX_COUNT = 10;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${BOCHA_API_KEY:}")
    private String bochaApiKey;

    public WebSearchTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String getToolName() {
        return "web_search";
    }

    @Override
    public String getToolDescription() {
        return "当用户明确要求'联网搜索/上网查/搜索最新信息/查一下最近'时使用,获取实时网络信息。"
                + "例如:『帮我上网搜一下2026年秋招时间』『查一下最新的AI新闻』。"
                + "输入:query(用户想要搜索的内容,原样传入,不要改写)、count(可选,返回结果数,默认5)。"
                + "输出:搜索结果列表(标题/链接/摘要)。"
                + "注意:① 仅当用户明确要求搜索网络时才调用——普通知识库问答、寒暄、常识问题绝不调用本工具;"
                + "② 知识库里的内容优先用file_search,不要用本工具。";
    }

    @Override
    public String execute(Map<String, Object> params) {
        log.info(">>> web_search被调用, 参数: {}", params);

        if (bochaApiKey == null || bochaApiKey.isBlank()) {
            throw new BusinessException("联网搜索未配置:请在环境变量或 .env 中设置 BOCHA_API_KEY(博查开放平台 open.bochaai.com 免费领取)");
        }

        Object rawQuery = params.get("query");
        if (rawQuery == null || rawQuery.toString().isBlank()) {
            throw new BusinessException("缺少参数query");
        }
        String query = rawQuery.toString().trim();

        int count = DEFAULT_COUNT;
        Object rawCount = params.get("count");
        if (rawCount != null) {
            try {
                count = Math.min(Integer.parseInt(rawCount.toString()), MAX_COUNT);
            } catch (NumberFormatException ignored) {
                // 非数字参数用默认值
            }
        }

        try {
            String rawJson = callBocha(query, count);
            return compactResults(rawJson, count);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("web_search 调用异常", e);
            throw new BusinessException("联网搜索失败,请稍后重试");
        }
    }

    private String callBocha(String query, int count) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("summary", true);
        body.put("freshness", "noLimit");
        body.put("count", count);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BOCHA_ENDPOINT))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bochaApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("博查 API 非200: status={}, body={}", response.statusCode(), truncate(response.body(), 200));
            throw new BusinessException("联网搜索服务异常(HTTP " + response.statusCode() + ")");
        }
        return response.body();
    }

    /**
     * 精简结果:只保留 name/url/summary(Bing 兼容格式 data.webPages.value[])
     */
    private String compactResults(String rawJson, int count) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode value = root.path("data").path("webPages").path("value");

        List<Map<String, Object>> items = new ArrayList<>();
        if (value.isArray()) {
            for (JsonNode page : value) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", page.path("name").asText(""));
                item.put("url", page.path("url").asText(""));
                String summary = page.path("summary").asText("");
                if (summary.isBlank()) {
                    summary = page.path("snippet").asText("");
                }
                item.put("summary", truncate(summary, SUMMARY_MAX));
                items.add(item);
                if (items.size() >= count) {
                    break;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("results", items);
        result.put("total", items.size());

        return objectMapper.writeValueAsString(result);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
