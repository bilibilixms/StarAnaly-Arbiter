package com.staranaly.arbiter.csv;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class CsvParser {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_ROWS = 50_000;
    private static final int MAX_COLUMNS = 50;
    private static final int MAX_FIELD_CHARS = 10_000;

    public CsvData parse(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new CsvInputException("CSV 文件必须非空且不超过 5 MiB。");
        }
        String source;
        try {
            source = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new CsvInputException("CSV 文件必须使用 UTF-8 编码。");
        }
        if (source.startsWith("\uFEFF")) {
            source = source.substring(1);
        }
        if (source.indexOf('\0') >= 0) {
            throw new CsvInputException("CSV 文件包含无效字符。");
        }

        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (quoted) {
                if (current == '"') {
                    if (i + 1 < source.length() && source.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                        closedQuote = true;
                    }
                } else {
                    field.append(current);
                }
            } else if (current == ',') {
                fields.add(field.toString());
                field.setLength(0);
                closedQuote = false;
            } else if (current == '\n' || current == '\r') {
                fields.add(field.toString());
                addRecord(records, fields);
                fields = new ArrayList<>();
                field.setLength(0);
                closedQuote = false;
                if (current == '\r' && i + 1 < source.length() && source.charAt(i + 1) == '\n') {
                    i++;
                }
            } else if (current == '"' && field.isEmpty() && !closedQuote) {
                quoted = true;
            } else if (closedQuote || current == '"') {
                throw new CsvInputException("CSV 引号格式不正确。");
            } else {
                field.append(current);
            }
            if (field.length() > MAX_FIELD_CHARS) {
                throw new CsvInputException("CSV 单元格超过 10,000 字符。");
            }
        }
        if (quoted) {
            throw new CsvInputException("CSV 引号未闭合。");
        }
        if (!fields.isEmpty() || !field.isEmpty() || closedQuote) {
            fields.add(field.toString());
            addRecord(records, fields);
        }
        if (records.isEmpty()) {
            throw new CsvInputException("CSV 文件缺少表头。");
        }
        List<String> headers = records.removeFirst();
        if (headers.stream().anyMatch(value -> value.isBlank() || value.length() > 100)
                || new HashSet<>(headers).size() != headers.size()) {
            throw new CsvInputException("CSV 表头不能为空、重复或超过 100 字符。");
        }
        for (List<String> row : records) {
            if (row.size() != headers.size()) {
                throw new CsvInputException("CSV 数据行与表头列数不一致。");
            }
        }
        return new CsvData(List.copyOf(headers), List.copyOf(records));
    }

    private static void addRecord(List<List<String>> records, List<String> fields) {
        if (fields.size() > MAX_COLUMNS) {
            throw new CsvInputException("CSV 列数不能超过 50。");
        }
        records.add(List.copyOf(fields));
        if (records.size() > MAX_ROWS + 1) {
            throw new CsvInputException("CSV 数据行不能超过 50,000 行。");
        }
    }
}
