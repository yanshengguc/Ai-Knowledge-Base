package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.service.splitter.MarkdownOutlineParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownOutlineParserTest {

    private final MarkdownOutlineParser parser = new MarkdownOutlineParser();

    @Test
    void 解析标题层级和完整路径() {
        String markdown = "# 架构\n\n## 接入层\n内容\n### 路由\n\n# 部署\n";

        List<MarkdownOutlineParser.MarkdownOutlineNode> nodes = parser.parse(markdown);

        assertEquals(4, nodes.size());
        assertEquals(List.of("架构"), nodes.get(0).headingPath());
        assertEquals(List.of("架构", "接入层"), nodes.get(1).headingPath());
        assertEquals(List.of("架构", "接入层", "路由"), nodes.get(2).headingPath());
        assertEquals(List.of("部署"), nodes.get(3).headingPath());
        assertEquals(3, nodes.get(2).level());
    }

    @Test
    void 忽略代码围栏里的伪标题并支持闭合标题标记() {
        String markdown = "```md\n# 不是标题\n```\n## 真标题 ##\n正文";

        List<MarkdownOutlineParser.MarkdownOutlineNode> nodes = parser.parse(markdown);

        assertEquals(1, nodes.size());
        assertEquals("真标题", nodes.get(0).title());
    }

    @Test
    void 闭围栏后带语言标记不能提前结束代码块() {
        String markdown = "```md\n# 代码标题\n```javascript\n# 仍是代码\n```\n# 真标题";

        List<MarkdownOutlineParser.MarkdownOutlineNode> nodes = parser.parse(markdown);

        assertEquals(1, nodes.size());
        assertEquals("真标题", nodes.get(0).title());
    }

    @Test
    void 混合围栏标记不应改变代码块状态() {
        String markdown = "```md\n# 代码标题\n``~`\n# 仍是代码\n```\n# 真标题";

        List<MarkdownOutlineParser.MarkdownOutlineNode> nodes = parser.parse(markdown);

        assertEquals(1, nodes.size());
        assertEquals("真标题", nodes.get(0).title());
    }

    @Test
    void 偏移量可以切回标题源码且兼容换行符() {
        String markdown = "前言\r\n# 第一章\r\n正文\n## 第二章\n";

        List<MarkdownOutlineParser.MarkdownOutlineNode> nodes = parser.parse(markdown);

        assertEquals(2, nodes.size());
        MarkdownOutlineParser.MarkdownOutlineNode first = nodes.get(0);
        MarkdownOutlineParser.MarkdownOutlineNode second = nodes.get(1);
        assertEquals("# 第一章", markdown.substring(first.sourceStartOffset(), first.sourceEndOffset()));
        assertEquals("## 第二章", markdown.substring(second.sourceStartOffset(), second.sourceEndOffset()));
        assertTrue(first.sourceEndOffset() < second.sourceStartOffset());
    }

    @Test
    void 空文本或无标题文本返回空列表() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("普通正文\n没有标题").isEmpty());
    }
}
