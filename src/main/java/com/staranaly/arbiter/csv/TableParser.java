package com.staranaly.arbiter.csv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

@Component
public class TableParser {

    private static final int MAX_COLUMNS = 50;
    private static final int MAX_FIELD_CHARS = 10_000;
    private final CsvParser csvParser;

    public TableParser(CsvParser csvParser) {
        this.csvParser = csvParser;
    }

    public CsvData parse(String name, byte[] bytes) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return csvParser.parse(bytes);
        }
        if (!lower.endsWith(".xls") && !lower.endsWith(".xlsx")) {
            throw new CsvInputException("只支持 CSV、XLS 或 XLSX 表格。");
        }
        if (bytes.length == 0 || bytes.length > CsvParser.MAX_BYTES) {
            throw new CsvInputException("表格文件必须非空且不超过 5 MiB。");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new CsvInputException("Excel 文件没有工作表。");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null || headerRow.getLastCellNum() < 1) {
                throw new CsvInputException("Excel 第一张工作表的首行必须是表头。");
            }
            int columnCount = headerRow.getLastCellNum();
            if (columnCount > MAX_COLUMNS) {
                throw new CsvInputException("Excel 列数不能超过 50。");
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            formatter.setUseCachedValuesForFormulaCells(true);
            List<String> headers = new ArrayList<>();
            for (int column = 0; column < columnCount; column++) {
                headers.add(value(headerRow.getCell(column), formatter));
            }
            if (headers.stream().anyMatch(header -> header.isBlank() || header.length() > 100)
                    || new HashSet<>(headers).size() != headers.size()) {
                throw new CsvInputException("Excel 表头不能为空、重复或超过 100 字符。");
            }
            if (sheet.getLastRowNum() > CsvParser.MAX_ROWS) {
                throw new CsvInputException("Excel 数据行不能超过 50,000 行。");
            }
            List<List<String>> rows = new ArrayList<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) {
                    continue;
                }
                if (row.getLastCellNum() > columnCount) {
                    for (int column = columnCount; column < row.getLastCellNum(); column++) {
                        if (!value(row.getCell(column), formatter).isBlank()) {
                            throw new CsvInputException("Excel 数据行不能超出表头列数。");
                        }
                    }
                }
                List<String> fields = new ArrayList<>();
                for (int column = 0; column < columnCount; column++) {
                    fields.add(value(row.getCell(column), formatter));
                }
                if (fields.stream().anyMatch(field -> !field.isBlank())) {
                    rows.add(List.copyOf(fields));
                }
            }
            return new CsvData(List.copyOf(headers), List.copyOf(rows));
        } catch (CsvInputException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new CsvInputException("无法读取 Excel 文件；请确认文件未损坏或加密。");
        }
    }

    private static String value(Cell cell, DataFormatter formatter) {
        String value;
        boolean numeric = cell != null && (cell.getCellType() == CellType.NUMERIC
                || (cell.getCellType() == CellType.FORMULA
                        && cell.getCachedFormulaResultType() == CellType.NUMERIC));
        if (numeric && DateUtil.isCellDateFormatted(cell)) {
            value = cell.getLocalDateTimeCellValue().toLocalDate().toString();
        } else if (numeric) {
            double number = cell.getNumericCellValue();
            if (!Double.isFinite(number)) {
                throw new CsvInputException("Excel 单元格包含无效数值。");
            }
            value = BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        } else {
            value = formatter.formatCellValue(cell);
        }
        if (value.length() > MAX_FIELD_CHARS || value.indexOf('\0') >= 0) {
            throw new CsvInputException("Excel 单元格包含无效字符或超过 10,000 字符。");
        }
        return value;
    }
}
