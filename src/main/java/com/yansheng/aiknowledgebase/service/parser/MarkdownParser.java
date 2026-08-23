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
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("Markdown 解析失败");
        }
    }
}
