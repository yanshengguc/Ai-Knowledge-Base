package com.yansheng.aiknowledgebase.service.parser;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ParserFactory {

    private static final String PDF = ".pdf";
    private static final String WORD = ".docx";

    private final PdfParser pdfParser;
    private final WordParser wordParser;


    public ParserFactory(PdfParser pdfParser,
                         WordParser wordParser) {
        this.pdfParser = pdfParser;
        this.wordParser = wordParser;
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
        throw new BusinessException("暂不支持该文件类型");
    }
}