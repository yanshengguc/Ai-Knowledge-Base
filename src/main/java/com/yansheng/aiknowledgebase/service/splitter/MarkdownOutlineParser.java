package com.yansheng.aiknowledgebase.service.splitter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯内存 Markdown 标题大纲解析器。
 *
 * 这是 B-114 的可逆扩展点:只解析标题层级、路径和源码偏移,
 * 不参与旧的 DocumentSplitter.split()、数据库写入或向量索引。
 */
public class MarkdownOutlineParser {

    private static final Pattern FENCE = Pattern.compile("^ {0,3}([`~]{3,}).*$");
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})[ \\t]+(.+?)\\s*$");

    /**
     * 解析 Markdown 标题。偏移量是 Java String 的 UTF-16 code unit 下标,
     * start 包含标题行起始位置,end 不包含换行符。
     */
    public List<MarkdownOutlineNode> parse(String markdown) {
        List<MarkdownOutlineNode> result = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return result;
        }

        Deque<MarkdownOutlineNode> stack = new ArrayDeque<>();
        String fenceMarker = null;
        int lineStart = 0;
        while (lineStart <= markdown.length()) {
            int lineBreak = markdown.indexOf('\n', lineStart);
            int lineEnd = lineBreak >= 0 ? lineBreak : markdown.length();
            String line = markdown.substring(lineStart, lineEnd);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }

            Matcher fenceMatcher = FENCE.matcher(line);
            if (fenceMatcher.matches()) {
                String marker = fenceMatcher.group(1);
                if (fenceMarker == null) {
                    fenceMarker = marker;
                } else if (marker.charAt(0) == fenceMarker.charAt(0)
                        && marker.length() >= fenceMarker.length()) {
                    fenceMarker = null;
                }
            } else if (fenceMarker == null) {
                Matcher headingMatcher = HEADING.matcher(line);
                if (headingMatcher.matches()) {
                    int level = headingMatcher.group(1).length();
                    String title = stripClosingHashes(headingMatcher.group(2));
                    if (!title.isBlank()) {
                        while (!stack.isEmpty() && stack.peek().level() >= level) {
                            stack.pop();
                        }
                        List<String> path = new ArrayList<>();
                        var parents = stack.descendingIterator();
                        while (parents.hasNext()) {
                            path.add(parents.next().title());
                        }
                        path.add(title);
                        MarkdownOutlineNode node = new MarkdownOutlineNode(
                                level,
                                title,
                                List.copyOf(path),
                                lineStart,
                                lineStart + line.length());
                        result.add(node);
                        stack.push(node);
                    }
                }
            }

            if (lineBreak < 0) {
                break;
            }
            lineStart = lineBreak + 1;
        }
        return result;
    }

    private String stripClosingHashes(String rawTitle) {
        String title = rawTitle.trim();
        return title.replaceFirst("[ \\t]+#+[ \\t]*$", "").trim();
    }

    public record MarkdownOutlineNode(
            int level,
            String title,
            List<String> headingPath,
            int sourceStartOffset,
            int sourceEndOffset) {
    }
}
