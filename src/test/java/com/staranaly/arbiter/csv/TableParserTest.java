package com.staranaly.arbiter.csv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableParserTest {

    private final TableParser parser = new TableParser(new CsvParser());

    @Test
    void readsXlsxAndXlsAsTableData() throws IOException {
        for (boolean legacy : List.of(false, true)) {
            try (Workbook workbook = legacy ? new HSSFWorkbook() : new XSSFWorkbook()) {
                var sheet = workbook.createSheet("销售额");
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("date");
                header.createCell(1).setCellValue("amount");
                Row row = sheet.createRow(1);
                row.createCell(0).setCellValue("2026-01-12");
                row.createCell(1).setCellValue(300);
                var moneyStyle = workbook.createCellStyle();
                moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));
                row.getCell(1).setCellStyle(moneyStyle);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                workbook.write(output);

                CsvData data = parser.parse(legacy ? "sales.xls" : "sales.xlsx", output.toByteArray());
                assertThat(data.headers()).containsExactly("date", "amount");
                assertThat(data.rows()).containsExactly(List.of("2026-01-12", "300"));
            }
        }
    }

    @Test
    void rejectsBrokenWorkbookAndDuplicateHeaders() throws IOException {
        assertThatThrownBy(() -> parser.parse("broken.xlsx", new byte[] {1, 2, 3}))
                .isInstanceOf(CsvInputException.class).hasMessageContaining("无法读取");
        try (Workbook workbook = new XSSFWorkbook()) {
            Row header = workbook.createSheet().createRow(0);
            header.createCell(0).setCellValue("amount");
            header.createCell(1).setCellValue("amount");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            assertThatThrownBy(() -> parser.parse("duplicate.xlsx", output.toByteArray()))
                    .isInstanceOf(CsvInputException.class).hasMessageContaining("表头");
        }
    }

    @Test
    void normalizesExcelDatesForMonthlyAggregation() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("date");
            header.createCell(1).setCellValue("amount");
            Row row = sheet.createRow(1);
            var date = row.createCell(0);
            date.setCellValue(LocalDateTime.of(2026, 1, 12, 0, 0));
            var dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            date.setCellStyle(dateStyle);
            row.createCell(1).setCellValue(300);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);

            CsvData data = parser.parse("sales.xlsx", output.toByteArray());
            assertThat(data.rows()).containsExactly(List.of("2026-01-12", "300"));
            assertThat(new CsvAnalyzer().aggregate(1L, data, "date", "amount", true, null, null)
                    .groups().getFirst().key()).isEqualTo("2026-01");
        }
    }
}
