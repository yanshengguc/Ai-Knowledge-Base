package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.common.tool.ToolTraceSummarizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 时间线摘要:工具返回 JSON → 人话;解析失败/未知工具降级截断原文。
 * 纯函数单测,不起 Spring。
 */
class ToolTraceSummarizerTest {

    @Test
    void shouldSummarizeFileSearchWithResults() {
        String json = "{\"results\":[{\"fileId\":1,\"score\":0.2,\"snippet\":\"a\"},{\"fileId\":2,\"score\":0.3,\"snippet\":\"b\"}]}";
        assertEquals("检索到 2 个相关文件", ToolTraceSummarizer.summarize("file_search", json));
    }

    @Test
    void shouldSummarizeFileSearchEmptyAsNoResult() {
        // 向量库故障降级路径返回空结果 JSON,时间线要如实说"未检索到",不能显示原始 JSON
        assertEquals("未检索到相关资料", ToolTraceSummarizer.summarize("file_search", "{\"results\":[]}"));
    }

    @Test
    void shouldSummarizeFileTraceWithFileName() {
        String json = "{\"fileName\":\"RAG笔记.md\",\"fileUrl\":\"http://oss/x\",\"fileSize\":1024,\"updateTime\":\"2026-09-05\"}";
        assertEquals("定位到文件《RAG笔记.md》", ToolTraceSummarizer.summarize("file_trace", json));
    }

    @Test
    void shouldSummarizeTimeNow() {
        String json = "{\"localTime\":\"2026-09-05 20:58:00\",\"weekday\":\"星期六\"}";
        assertEquals("2026-09-05 20:58:00 星期六", ToolTraceSummarizer.summarize("time_now", json));
    }

    @Test
    void shouldSummarizeKnowledgeStats() {
        String json = "{\"knowledgeCount\":3,\"fileCount\":5,\"chunkCount\":120,\"statusSummary\":{\"SUCCESS\":5}}";
        assertEquals("知识库共 3 知识 / 5 文件 / 120 切片", ToolTraceSummarizer.summarize("knowledge_stats", json));
    }

    @Test
    void shouldAppendProcessingCountForKnowledgeStats() {
        String json = "{\"knowledgeCount\":3,\"fileCount\":5,\"chunkCount\":120,\"statusSummary\":{\"SUCCESS\":4,\"PROCESSING\":1}}";
        assertEquals("知识库共 3 知识 / 5 文件 / 120 切片(处理中 1)", ToolTraceSummarizer.summarize("knowledge_stats", json));
    }

    @Test
    void shouldSummarizeWebSearch() {
        assertEquals("联网搜索到 3 条结果",
                ToolTraceSummarizer.summarize("web_search", "{\"results\":[{},{},{}],\"total\":3}"));
        assertEquals("联网搜索无结果",
                ToolTraceSummarizer.summarize("web_search", "{\"results\":[],\"total\":0}"));
    }

    @Test
    void shouldFallbackToTruncatedRawOnFailureText() {
        // executeTool 失败回传的是纯文本(非 JSON):原样截断展示,失败信息不吞
        String failure = "工具执行失败: 缺少参数query(请换一种方式处理或如实告知用户)";
        assertEquals(failure, ToolTraceSummarizer.summarize("file_search", failure));
    }

    @Test
    void shouldFallbackToTruncatedRawOnUnknownTool() {
        String json = "{\"foo\":\"bar\"}";
        assertEquals("{\"foo\":\"bar\"}", ToolTraceSummarizer.summarize("unknown_tool", json));
    }

    @Test
    void shouldTruncateLongRawJson() {
        String longJson = "{\"results\":[" + "x".repeat(300) + "]}";
        String summarized = ToolTraceSummarizer.summarize("mystery", longJson);
        assertTrue(summarized.length() <= 121, "截断后不超过 120 字符 + 省略号");
        assertTrue(summarized.endsWith("…"));
    }

    @Test
    void shouldHandleNullOrBlank() {
        assertEquals("", ToolTraceSummarizer.summarize("file_search", null));
        assertEquals("", ToolTraceSummarizer.summarize("file_search", "  "));
        assertEquals("", ToolTraceSummarizer.summarize(null, null));
    }

    @Test
    void shouldFallbackWhenKnownToolGetsUnexpectedStructure() {
        // 已知工具但结构对不上(如 file_trace 缺 fileName)→ 降级截断原文,不编造
        String json = "{\"unexpected\":true}";
        assertEquals(json, ToolTraceSummarizer.summarize("file_trace", json));
    }
}
