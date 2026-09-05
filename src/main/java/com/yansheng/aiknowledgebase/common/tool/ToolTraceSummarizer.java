package com.yansheng.aiknowledgebase.common.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent 工具轨迹摘要:把工具返回的 JSON 翻译成一句人话,供前端时间线展示。
 *
 * 设计:
 *  - 已知工具 → 按返回结构取关键字段生成摘要(空结果也有明确文案)
 *  - 未知工具 / 非 JSON / 解析失败 → 降级截断原文展示(时间线如实反映,不编造)
 *  - 纯函数无副作用,编排层(FunctionCallingServiceImpl)调用,不改变回传给模型的原始结果
 */
public final class ToolTraceSummarizer {

    /** 摘要展示截断上限,与轨迹事件的 TRACE_MAX_LEN 口径一致 */
    private static final int MAX_LEN = 120;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolTraceSummarizer() {
    }

    public static String summarize(String toolName, String result) {
        if (result == null || result.isBlank()) {
            return "";
        }
        try {
            JsonNode root = MAPPER.readTree(result);
            if (!root.isObject()) {
                return truncate(result);
            }
            String text = switch (toolName == null ? "" : toolName) {
                case "file_search" -> summarizeFileSearch(root);
                case "file_trace" -> summarizeFileTrace(root);
                case "time_now" -> summarizeTimeNow(root);
                case "knowledge_stats" -> summarizeKnowledgeStats(root);
                case "web_search" -> summarizeWebSearch(root);
                default -> null;
            };
            return text != null ? text : truncate(result);
        } catch (Exception e) {
            // 纯文本结果(如"工具执行失败: ...")原样截断展示,失败信息不吞
            return truncate(result);
        }
    }

    /** file_search: {"results":[{fileId,score,snippet},...]} */
    private static String summarizeFileSearch(JsonNode root) {
        JsonNode results = root.path("results");
        int count = results.isArray() ? results.size() : 0;
        return count > 0 ? "检索到 " + count + " 个相关文件" : "未检索到相关资料";
    }

    /** file_trace: {"fileName":...,"fileUrl":...,...} */
    private static String summarizeFileTrace(JsonNode root) {
        String fileName = root.path("fileName").asText("");
        if (fileName.isBlank()) {
            return null;
        }
        return "定位到文件《" + fileName + "》";
    }

    /** time_now: {"localTime":"...","weekday":"星期六"} */
    private static String summarizeTimeNow(JsonNode root) {
        String localTime = root.path("localTime").asText("");
        if (localTime.isBlank()) {
            return null;
        }
        String weekday = root.path("weekday").asText("");
        return weekday.isBlank() ? localTime : localTime + " " + weekday;
    }

    /** knowledge_stats: {"knowledgeCount":N,"fileCount":N,"chunkCount":N,"statusSummary":{...}} */
    private static String summarizeKnowledgeStats(JsonNode root) {
        if (root.path("chunkCount").isMissingNode()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("知识库共 ")
                .append(root.path("knowledgeCount").asInt(0)).append(" 知识 / ")
                .append(root.path("fileCount").asInt(0)).append(" 文件 / ")
                .append(root.path("chunkCount").asInt(0)).append(" 切片");
        int processing = root.path("statusSummary").path("PROCESSING").asInt(0);
        if (processing > 0) {
            sb.append("(处理中 ").append(processing).append(")");
        }
        return sb.toString();
    }

    /** web_search: {"results":[...],"total":N} */
    private static String summarizeWebSearch(JsonNode root) {
        JsonNode results = root.path("results");
        if (results.isMissingNode()) {
            return null;
        }
        int total = root.path("total").asInt(results.isArray() ? results.size() : 0);
        return total > 0 ? "联网搜索到 " + total + " 条结果" : "联网搜索无结果";
    }

    private static String truncate(String s) {
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() <= MAX_LEN ? flat : flat.substring(0, MAX_LEN) + "…";
    }
}
