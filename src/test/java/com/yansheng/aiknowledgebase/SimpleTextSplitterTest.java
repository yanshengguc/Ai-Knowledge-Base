package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.service.splitter.SimpleTextSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 切片器回归测试(修复假测试:原测试 0 断言,只打印)
 * 验证:切片数量/长度/覆盖全文/参数校验
 */
class SimpleTextSplitterTest {

    @Test
    void testSplitWithOverlap() {
        SimpleTextSplitter splitter = new SimpleTextSplitter(5, 2);
        List<String> result = splitter.split("ABCDEFGHIJK");

        // "ABCDEFGHIJK"(11 字符),chunkSize=5, overlap=2 → 3 块:ABCDE / DEFGH / GHIJK
        assertNotNull(result);
        assertEquals(3, result.size(), "应切成 3 块");
        assertEquals("ABCDE", result.get(0));
        assertEquals("DEFGH", result.get(1));
        assertEquals("GHIJK", result.get(2));
        // 相邻块必须有重叠(overlap 生效):块1 尾部 2 位 = 块2 头部 2 位
        assertEquals(result.get(0).substring(3), result.get(1).substring(0, 2), "第二块应复用第一块尾部(重叠)");
    }

    @Test
    void testEveryChunkWithinMaxSize() {
        SimpleTextSplitter splitter = new SimpleTextSplitter(5, 2);
        for (String chunk : splitter.split("ABCDEFGHIJKLMNOPQRSTUVWXYZ")) {
            assertTrue(chunk.length() <= 5, "每块长度不得超过 chunkSize");
        }
    }

    @Test
    void testAllContentCovered() {
        // 拼接所有块,原文每个字符至少出现在一块里
        String original = "这是一段用于测试切片是否正确覆盖全文的中文文本";
        SimpleTextSplitter splitter = new SimpleTextSplitter(8, 2);
        StringBuilder joined = new StringBuilder();
        for (String chunk : splitter.split(original)) {
            joined.append(chunk);
        }
        for (char c : original.toCharArray()) {
            assertTrue(joined.toString().indexOf(c) >= 0, "字符不应丢失: " + c);
        }
    }

    @Test
    void testInvalidParamsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleTextSplitter(0, 0), "chunkSize 必须 > 0");
        assertThrows(IllegalArgumentException.class, () -> new SimpleTextSplitter(5, -1), "overlap 不能为负");
        assertThrows(IllegalArgumentException.class, () -> new SimpleTextSplitter(5, 5), "overlap 必须 < chunkSize");
    }

    @Test
    void testEmptyAndNullText() {
        SimpleTextSplitter splitter = new SimpleTextSplitter(5, 2);
        assertTrue(splitter.split("").isEmpty(), "空文本返回空列表");
        assertTrue(splitter.split(null).isEmpty(), "null 文本返回空列表");
    }
}
