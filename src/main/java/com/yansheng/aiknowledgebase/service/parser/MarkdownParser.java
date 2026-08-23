package com.yansheng.aiknowledgebase.service.parser;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * Markdown 解析器:md 是纯文本,直接按 UTF-8 读入。
 * 配合写优先理念——Markdown 是知识沉淀最自然的载体,与笔记走同一套切片+向量化管线。
 */
@Component
public class MarkdownParser implements DocumentParser {

    @Override
    public String parse(MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            // 去 BOM:Windows 记事本等编辑器保存的 md 常带 UTF-8 BOM,
            // 不去掉会把 \uFEFF 带进首个切片,污染分词与向量化
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }
            return content;
        } catch (Exception e) {
            throw new BusinessException("Markdown 解析失败");
        }
    }
}
