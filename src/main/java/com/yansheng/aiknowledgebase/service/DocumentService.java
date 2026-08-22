package com.yansheng.aiknowledgebase.service;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {
    void handleDocument(MultipartFile file, Long fileId);

    /**
     * 纯文本索引(写优先:笔记/手写 Markdown 直接切片+向量化,复用文件同一条管线)。
     * 与 handleDocument 的区别:跳过文档解析器,text 直接切片。
     */
    void indexPlainText(Long fileId, String text);
}
