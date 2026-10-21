package com.staranaly.arbiter.knowledge;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDocumentParser {

    public static final int MAX_BINARY_BYTES = 5 * 1024 * 1024;
    private static final int MAX_PAGES = 100;
    private final KnowledgeTextProcessor processor;

    public KnowledgeDocumentParser(KnowledgeTextProcessor processor) {
        this.processor = processor;
    }

    public String parse(String name, byte[] bytes) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return processor.decode(bytes);
        }
        if (bytes.length == 0 || bytes.length > MAX_BINARY_BYTES) {
            throw new KnowledgeInputException("PDF 或 Word 文件必须非空且不超过 5 MiB。");
        }
        String text;
        try {
            if (lower.endsWith(".pdf")) {
                text = pdf(bytes);
            } else if (lower.endsWith(".docx")) {
                try (XWPFWordExtractor extractor = new XWPFWordExtractor(
                        new XWPFDocument(new ByteArrayInputStream(bytes)))) {
                    text = extractor.getText();
                }
            } else if (lower.endsWith(".doc")) {
                try (WordExtractor extractor = new WordExtractor(new ByteArrayInputStream(bytes))) {
                    text = extractor.getText();
                }
            } else {
                throw new KnowledgeInputException("只支持 TXT、Markdown、PDF 或 Word 资料。");
            }
        } catch (KnowledgeInputException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new KnowledgeInputException("无法读取 PDF 或 Word 文件；请确认文件未损坏或加密。");
        }
        if (text == null || text.isBlank()) {
            throw new KnowledgeInputException("文件没有可提取的文字；扫描版 PDF 需要先进行 OCR。");
        }
        if (text.length() > KnowledgeTextProcessor.MAX_BYTES) {
            throw new KnowledgeInputException("提取的资料文字不能超过 1 MiB。");
        }
        byte[] extracted = text.getBytes(StandardCharsets.UTF_8);
        if (extracted.length > KnowledgeTextProcessor.MAX_BYTES) {
            throw new KnowledgeInputException("提取的资料文字不能超过 1 MiB。");
        }
        return processor.decode(extracted);
    }

    private static String pdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new KnowledgeInputException("PDF 页数不能超过 100 页。");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            StringBuilder content = new StringBuilder();
            boolean hasText = false;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document).replace('\f', '\n').strip();
                if (!pageText.isBlank()) {
                    hasText = true;
                    content.append("第 ").append(page).append(" 页\n").append(pageText).append("\n\n");
                }
                if (content.length() > KnowledgeTextProcessor.MAX_BYTES) {
                    throw new KnowledgeInputException("提取的资料文字不能超过 1 MiB。");
                }
            }
            return hasText ? content.toString() : "";
        }
    }
}
