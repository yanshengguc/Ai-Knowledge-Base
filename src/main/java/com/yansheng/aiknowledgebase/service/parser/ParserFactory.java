package com.yansheng.aiknowledgebase.service.parser;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ParserFactory {

    private static final String PDF = ".pdf";
    private static final String WORD = ".docx";
    private static final String MARKDOWN = ".md";

    private final PdfParser pdfParser;
    private final WordParser wordParser;
    private final MarkdownParser markdownParser;


    public ParserFactory(PdfParser pdfParser,
                         WordParser wordParser,
                         MarkdownParser markdownParser) {
        this.pdfParser = pdfParser;
        this.wordParser = wordParser;
        this.markdownParser = markdownParser;
    }

    public DocumentParser getParser(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new BusinessException("文件名不能为空");
        }
        fileName = fileName.toLowerCase();
        if (fileName.endsWith(PDF)) {
            return pdfParser;
        }
        if (fileName.endsWith(WORD)) {
            return wordParser;
        }
        if (fileName.endsWith(MARKDOWN)) {
            return markdownParser;
        }
        throw new BusinessException("暂不支持该文件类型");
    }
}
