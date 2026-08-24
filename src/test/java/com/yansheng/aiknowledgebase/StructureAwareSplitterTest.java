package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.service.splitter.StructureAwareSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构感知切片单元测试:全部纯内存验证,不依赖 Spring/外部服务。
 * 覆盖:标题分节、chunk 前置标题、段落打包不越界、超长段句子级拆、
 *       超长句硬切兜底、无结构文本、空输入。
 */
class StructureAwareSplitterTest {

    private static final String CJK_SENTENCE = "结构感知切片保持语义完整。";
    private static final int CJK_SENTENCE_LEN = CJK_SENTENCE.length();

    @Test
    void 标题分节_每个chunk带所属标题() {
        String text = """
                # 架构设计
                系统分为三层。接入层负责路由。

                # 部署方案
                单机部署即可。Nginx 做反代。
                """;
        List<String> chunks = new StructureAwareSplitter(500, 100).split(text);

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).startsWith("# 架构设计"));
        assertTrue(chunks.get(0).contains("接入层负责路由"));
        assertTrue(chunks.get(1).startsWith("# 部署方案"));
        assertTrue(chunks.get(1).contains("Nginx 做反代"));
    }

    @Test
    void 段落打包_装满即切_不超上限() {
        // 三个段落各 ~300 字符,chunkSize=500 → 应切成多块,每块 ≤ 500
        String p1 = CJK_SENTENCE.repeat(30);   // 330
        String p2 = CJK_SENTENCE.repeat(30);
        String p3 = CJK_SENTENCE.repeat(10);
        String text = "# 标题\n\n" + p1 + "\n\n" + p2 + "\n\n" + p3;

        List<String> chunks = new StructureAwareSplitter(500, 100).split(text);

        assertTrue(chunks.size() >= 2, "三段 990 字符按 500 上限至少切 2 块,实际: " + chunks.size());
        for (String c : chunks) {
            assertTrue(c.length() <= 500, "chunk 越界: " + c.length());
            assertTrue(c.startsWith("# 标题"), "chunk 缺章节标题: " + c.substring(0, 20));
        }
        // 段落不被拆散:p1 完整出现在某个 chunk 中
        assertTrue(chunks.stream().anyMatch(c -> c.contains(p1)), "完整段落应整体落在一个 chunk");
    }

    @Test
    void 超长段落_句子级拆分() {
        // 单段 2000 字符 > 500 → 句子级打包
        String longPara = CJK_SENTENCE.repeat(200);
        List<String> chunks = new StructureAwareSplitter(500, 100).split("# 标题\n\n" + longPara);

        assertTrue(chunks.size() >= 4, "2000 字符按 500 上限至少 4 块,实际: " + chunks.size());
        for (String c : chunks) {
            assertTrue(c.length() <= 500 + "# 标题\n".length(), "句子级 chunk 越界: " + c.length());
        }
        // 句子完整性:任何 chunk 内部不该出现"切了一半的句子"(首句完整、末句完整)
        for (String c : chunks) {
            String body = c.replaceFirst("^# 标题\n", "");
            assertTrue(body.startsWith("结构感知"), "句子被拦腰切断(头): " + body.substring(0, 15));
            assertTrue(body.endsWith("。"), "句子被拦腰切断(尾): ..." + body.substring(body.length() - 10));
        }
    }

    @Test
    void 超长句子_硬切兜底() {
        // 一整句 1200 字符无任何句读 → 句子级拆不动 → 硬切兜底
        String monster = "无".repeat(1200) + "。";
        List<String> chunks = new StructureAwareSplitter(500, 100).split(monster);

        assertTrue(chunks.size() >= 3);
        for (String c : chunks) {
            assertTrue(c.length() <= 500);
        }
    }

    @Test
    void 无标题纯文本_按段落打包() {
        String text = "第一段内容甲。" + "乙".repeat(80) + "\n\n第二段内容丙。";
        List<String> chunks = new StructureAwareSplitter(500, 100).split(text);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("第一段内容甲"));
        assertTrue(chunks.get(0).contains("第二段内容丙"));
    }

    @Test
    void 空与null_返回空列表() {
        assertEquals(0, new StructureAwareSplitter(500, 100).split(null).size());
        assertEquals(0, new StructureAwareSplitter(500, 100).split("   \n\n  ").size());
    }

    @Test
    void 英文句点_不误切小数与缩写内文() {
        String text = "# En\n\nVersion 3.14 is pi. Next sentence here.";
        List<String> chunks = new StructureAwareSplitter(500, 100).split(text);
        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("3.14"), "小数点不应被当句界切断");
    }
}
