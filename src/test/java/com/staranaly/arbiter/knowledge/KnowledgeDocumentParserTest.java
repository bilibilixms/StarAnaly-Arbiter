package com.staranaly.arbiter.knowledge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeDocumentParserTest {

    private final KnowledgeDocumentParser parser = new KnowledgeDocumentParser(new KnowledgeTextProcessor());

    @Test
    void extractsWordText() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Returns within 14 days.");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            assertThat(parser.parse("rules.docx", output.toByteArray()))
                    .contains("Returns within 14 days.");
        }
    }

    @Test
    void extractsPdfTextWithPageMarker() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(40, 700);
                stream.showText("Returns within 14 days.");
                stream.endText();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            assertThat(parser.parse("rules.pdf", output.toByteArray()))
                    .contains("第 1 页", "Returns within 14 days.");
        }
    }

    @Test
    void rejectsUnreadableAndEmptyDocuments() {
        assertThatThrownBy(() -> parser.parse("broken.pdf", "not a PDF".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(KnowledgeInputException.class).hasMessageContaining("无法读取");
        assertThatThrownBy(() -> parser.parse("empty.docx", new byte[0]))
                .isInstanceOf(KnowledgeInputException.class).hasMessageContaining("非空");
    }

    @Test
    void explainsWhenPdfHasNoExtractableText() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            assertThatThrownBy(() -> parser.parse("scan.pdf", output.toByteArray()))
                    .isInstanceOf(KnowledgeInputException.class).hasMessageContaining("OCR");
        }
    }
}
