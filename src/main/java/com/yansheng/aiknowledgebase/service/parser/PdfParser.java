package com.yansheng.aiknowledgebase.service.parser;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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
            String text = stripper.getText(document);

            // 空文本层 = 扫描件/图片型 PDF:静默入库会让文件"假成功"却永远检索不到,必须显式失败。
            // 区分"有图片的扫描件"和"真空文件",给用户可行动的提示。
            if (text == null || text.isBlank()) {
                if (hasImages(document)) {
                    throw new BusinessException(
                            "该 PDF 无文字层(可能是扫描件或图片型 PDF),无法提取文字内容;请使用可选中复制文字的 PDF");
                }
                throw new BusinessException("该 PDF 内容为空,无法提取文字内容");
            }

            return text;

        } catch (IOException e) {

            log.error("PDF解析失败,fileName={}",
                    file.getOriginalFilename(),
                    e);

            throw new BusinessException("文档解析失败");
        }
    }

    /** 页面上挂了图片资源即视为含图片(扫描件的典型特征;不做 OCR,只用于失败原因归因) */
    private boolean hasImages(PDDocument document) {
        for (PDPage page : document.getPages()) {
            if (page.getResources() != null
                    && page.getResources().getXObjectNames().iterator().hasNext()) {
                return true;
            }
        }
        return false;
    }
}
