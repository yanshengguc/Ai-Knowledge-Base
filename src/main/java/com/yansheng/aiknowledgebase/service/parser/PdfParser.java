package com.yansheng.aiknowledgebase.service.parser;

import com.yansheng.aiknowledgebase.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
@Component
@Slf4j
public class PdfParser implements DocumentParser {

    @Override
    public String parse(MultipartFile file) {

        try (InputStream inputStream = file.getInputStream();
             PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {

            PDFTextStripper stripper = new PDFTextStripper();

            return stripper.getText(document);

        } catch (IOException e) {

            log.error("PDF解析失败,fileName={}",
                    file.getOriginalFilename(),
                    e);

            throw new BusinessException("文档解析失败");
        }
    }
}