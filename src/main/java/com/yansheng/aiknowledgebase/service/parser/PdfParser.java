package com.yansheng.aiknowledgebase.service.parser;

import com.yansheng.aiknowledgebase.common.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public class PdfParser implements DocumentParser {

    @Override
    public String parse(MultipartFile file) {

        try (InputStream inputStream = file.getInputStream();
             PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {

            PDFTextStripper stripper = new PDFTextStripper();

            String text = stripper.getText(document);

            return text;

        } catch (IOException e) {

            throw new BusinessException("文档解析失败");

        }
    }
}