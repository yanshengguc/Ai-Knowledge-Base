package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.parser.PdfParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-边界: 扫描版/图片型 PDF 必须显式失败,而不是"假成功入库却永远检索不到"。
 * 用 PDFBox 现场构造三类 PDF(空白/含图无字/正常文字),不依赖外部样例文件。
 */
class PdfParserTest {

    private final PdfParser parser = new PdfParser();

    private MockMultipartFile asMultipart(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return new MockMultipartFile("file", "test.pdf", "application/pdf", out.toByteArray());
    }

    @Test
    void scannedPdfWithImagesButNoTextFailsWithActionableMessage() throws IOException {
        // 纯图片页(模拟扫描件):一张 1x1 图片铺满页面,无任何文字
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, Color.WHITE.getRGB());
            PDImageXObject x = PDImageXObject.createFromByteArray(doc,
                    toPngBytes(img), "scan");
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(x, 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
            }
            BusinessException e = assertThrows(BusinessException.class,
                    () -> parser.parse(asMultipart(doc)));
            assertTrue(e.getMessage().contains("文字层"), "应提示无文字层,实际=" + e.getMessage());
            assertTrue(e.getMessage().contains("扫描"), "应提示可能是扫描件,实际=" + e.getMessage());
        }
    }

    @Test
    void trulyEmptyPdfFailsWithEmptyMessage() throws IOException {
        // 连图片都没有的空白 PDF:归因为"内容为空"而非"扫描件"
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            BusinessException e = assertThrows(BusinessException.class,
                    () -> parser.parse(asMultipart(doc)));
            assertTrue(e.getMessage().contains("内容为空"), "实际=" + e.getMessage());
        }
    }

    @Test
    void normalTextPdfParses() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("hello knowledge base");
                cs.endText();
            }
            String text = parser.parse(asMultipart(doc));
            assertTrue(text.contains("hello knowledge base"));
        }
    }

    private byte[] toPngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", png);
        return png.toByteArray();
    }
}
