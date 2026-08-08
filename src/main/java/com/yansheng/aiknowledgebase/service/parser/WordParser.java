package com.yansheng.aiknowledgebase.service.parser;

import com.yansheng.aiknowledgebase.common.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
@Component
public class WordParser implements DocumentParser {


    @Override
    public String parse(MultipartFile file) {

        try(InputStream inputStream = file.getInputStream()) {

            XWPFDocument document = new XWPFDocument(inputStream);

            StringBuilder builder = new StringBuilder();


            document.getParagraphs()
                    .forEach(paragraph ->
                            builder.append(paragraph.getText())
                                    .append("\n")
                    );


            return builder.toString();


        } catch (Exception e) {

            throw new BusinessException("文档解析失败");

        }

    }
}