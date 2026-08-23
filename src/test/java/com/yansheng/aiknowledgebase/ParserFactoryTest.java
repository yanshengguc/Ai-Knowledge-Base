package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.parser.DocumentParser;
import com.yansheng.aiknowledgebase.service.parser.MarkdownParser;
import com.yansheng.aiknowledgebase.service.parser.ParserFactory;
import com.yansheng.aiknowledgebase.service.parser.PdfParser;
import com.yansheng.aiknowledgebase.service.parser.WordParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** ParserFactory 路由:md 必须走 MarkdownParser(8/23 新增 md 上传支持) */
class ParserFactoryTest {

    private final ParserFactory factory =
            new ParserFactory(new PdfParser(), new WordParser(), new MarkdownParser());

    @Test
    void markdownRoutesToMarkdownParser() {
        DocumentParser parser = factory.getParser(file("学习笔记.md"));
        assertInstanceOf(MarkdownParser.class, parser);
    }

    @Test
    void pdfAndWordStillRouteCorrectly() {
        assertInstanceOf(PdfParser.class, factory.getParser(file("报告.pdf")));
        assertInstanceOf(WordParser.class, factory.getParser(file("文档.docx")));
    }

    @Test
    void unsupportedTypeRejected() {
        assertThrows(BusinessException.class, () -> factory.getParser(file("病毒.exe")));
    }

    private org.springframework.web.multipart.MultipartFile file(String name) {
        return new MockMultipartFile("file", name, "text/plain", "hello".getBytes());
    }
}
