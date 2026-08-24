package com.yansheng.aiknowledgebase.service.splitter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构感知切片:按 Markdown 标题 → 段落 → 句子三级边界切,语义优先于长度。
 *
 * 与 SimpleTextSplitter(固定窗口+重叠)的差异:
 *  - 标题/段落边界处绝不切断,答案所在句子与上下文同 chunk,检索命中的是完整语义单元
 *  - 每个 chunk 前置所属标题(Chunk Header 技巧),向量化自带章节上下文,
 *    解决"片段脱离章节后语义漂移"(如多个章节都有'配置'小节)
 *  - 超长段落降级句子级打包,超长句子最后才硬切(固定窗口+重叠兜底)
 *
 * 配置 splitter.mode: structure(默认)/ simple 一键切换,评估脚本可对比两种策略。
 */
@Component
@ConditionalOnProperty(name = "splitter.mode", havingValue = "structure", matchIfMissing = true)
public class StructureAwareSplitter implements DocumentSplitter {

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s.*");
    /** 句界:中文终止符直接切;英文句点要求后跟空白,避免 3.14 / e.g. 被误切 */
    private static final Pattern SENTENCE_END = Pattern.compile("[。！？!?；;]|\\.(?=\\s|$)");

    private final int chunkSize;
    private final int overlap;

    public StructureAwareSplitter(
            @Value("${splitter.chunk-size:500}") int chunkSize,
            @Value("${splitter.overlap:100}") int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize必须大于0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap参数非法");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        for (Section section : splitSections(text)) {
            emitSection(section, chunks);
        }
        return chunks;
    }

    /** 标题分节:每个 Markdown 标题开启新节,标题行归属本节(无标题正文归入空前导节) */
    private List<Section> splitSections(String text) {
        List<Section> sections = new ArrayList<>();
        Section current = new Section(null);
        for (String line : text.split("\n", -1)) {
            if (HEADING.matcher(line).matches()) {
                if (!current.body.isEmpty()) {
                    sections.add(current);
                }
                current = new Section(line.trim());
            } else {
                current.body.add(line);
            }
        }
        if (!current.body.isEmpty() || current.heading != null) {
            sections.add(current);
        }
        return sections;
    }

    /** 一节内:段落打包 → 超长段落句子级拆 → 超长句子硬切;每个 chunk 前置标题上下文 */
    private void emitSection(Section section, List<String> out) {
        String header = section.heading != null ? section.heading + "\n" : "";

        List<String> paragraphs = new ArrayList<>();
        StringBuilder para = new StringBuilder();
        for (String line : section.body) {
            if (line.isBlank()) {
                if (!para.isEmpty()) {
                    paragraphs.add(para.toString().trim());
                    para.setLength(0);
                }
            } else {
                para.append(line.trim()).append('\n');
            }
        }
        if (!para.isEmpty()) {
            paragraphs.add(para.toString().trim());
        }

        // 段落级贪心打包(同节段落语义相关,装满一个 chunk 为止)
        List<String> packed = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String p : paragraphs) {
            if (p.length() > chunkSize) {
                if (!buf.isEmpty()) {
                    packed.add(buf.toString());
                    buf.setLength(0);
                }
                packed.addAll(splitLongParagraph(p));
                continue;
            }
            if (!buf.isEmpty() && header.length() + buf.length() + p.length() + 1 > chunkSize) {
                packed.add(buf.toString());
                buf.setLength(0);
            }
            if (!buf.isEmpty()) {
                buf.append('\n');
            }
            buf.append(p);
        }
        if (!buf.isEmpty()) {
            packed.add(buf.toString());
        }

        for (String body : packed) {
            String chunk = header + body;
            out.add(chunk.stripTrailing());
        }
    }

    /** 超长段落:句子级贪心打包,并按 overlap 预算把上一 chunk 末句带到下一 chunk(句级重叠) */
    private List<String> splitLongParagraph(String paragraph) {
        List<String> sentences = splitSentences(paragraph);
        List<String> result = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        List<String> carried = new ArrayList<>();

        for (String s : sentences) {
            // 超长句子:硬切兜底(固定窗口+重叠,保证 chunkSize 上界)
            if (s.length() > chunkSize) {
                if (!buf.isEmpty()) {
                    result.add(buf.toString());
                    buf.setLength(0);
                    carried.clear();
                }
                result.addAll(hardSplit(s));
                continue;
            }
            if (!buf.isEmpty() && buf.length() + s.length() > chunkSize) {
                result.add(buf.toString());
                // 句级重叠:从尾部回取总长不超过 overlap 的句子带入下一段
                carried.clear();
                int budget = overlap;
                for (int i = sentences.indexOf(s) - 1; i >= 0 && budget > 0; i--) {
                    String prev = sentences.get(i);
                    if (prev.length() <= budget) {
                        carried.add(0, prev);
                        budget -= prev.length();
                    } else {
                        break;
                    }
                }
                buf.setLength(0);
                for (String c : carried) {
                    buf.append(c);
                }
            }
            buf.append(s);
        }
        if (!buf.isEmpty()) {
            result.add(buf.toString());
        }
        return result;
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        Matcher m = SENTENCE_END.matcher(text);
        int start = 0;
        while (m.find()) {
            String s = text.substring(start, m.end()).trim();
            if (!s.isEmpty()) {
                sentences.add(s);
            }
            start = m.end();
        }
        if (start < text.length()) {
            String tail = text.substring(start).trim();
            if (!tail.isEmpty()) {
                sentences.add(tail);
            }
        }
        return sentences;
    }

    /** 最后兜底:无任何结构的超长串,按固定窗口+重叠硬切 */
    private List<String> hardSplit(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            result.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start += chunkSize - overlap;
        }
        return result;
    }

    private static class Section {
        final String heading;
        final List<String> body = new ArrayList<>();

        Section(String heading) {
            this.heading = heading;
        }
    }
}
