package com.staranaly.arbiter.csv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

@Component
public class CsvAnalyzer {

    public List<Map<String, String>> rows(CsvData data, String filterColumn, String filterValue, int limit) {
        if (limit < 1 || limit > 100) {
            throw new CsvInputException("预览行数必须在 1 到 100 之间。");
        }
        int filterIndex = filterIndex(data, filterColumn, filterValue);
        List<Map<String, String>> result = new ArrayList<>();
        for (List<String> row : data.rows()) {
            if (filterIndex >= 0 && !row.get(filterIndex).equals(filterValue)) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            for (int i = 0; i < data.headers().size(); i++) {
                item.put(data.headers().get(i), row.get(i));
            }
            result.add(item);
            if (result.size() == limit) {
                break;
            }
        }
        return List.copyOf(result);
    }

    public CsvAggregateResponse aggregate(Long fileId, CsvData data, String groupBy, String sumColumn,
            boolean byMonth, String filterColumn, String filterValue) {
        int groupIndex = columnIndex(data, groupBy);
        int sumIndex = columnIndex(data, sumColumn);
        int filterIndex = filterIndex(data, filterColumn, filterValue);
        Map<String, Accumulator> groups = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        int matched = 0;
        for (List<String> row : data.rows()) {
            if (filterIndex >= 0 && !row.get(filterIndex).equals(filterValue)) {
                continue;
            }
            String key = row.get(groupIndex);
            if (byMonth) {
                try {
                    key = YearMonth.from(LocalDate.parse(key)).toString();
                } catch (DateTimeParseException exception) {
                    throw new CsvInputException("按月汇总的分组列必须使用 YYYY-MM-DD 日期。");
                }
            }
            String number = row.get(sumIndex).trim();
            if (!number.matches("[+-]?[0-9]{1,12}(\\.[0-9]{1,6})?")) {
                throw new CsvInputException("求和列包含无效数值。");
            }
            BigDecimal value = new BigDecimal(number);
            if (!groups.containsKey(key) && groups.size() >= 100) {
                throw new CsvInputException("汇总分组不能超过 100 组。");
            }
            groups.computeIfAbsent(key, ignored -> new Accumulator()).add(value);
            total = total.add(value);
            matched++;
        }
        List<CsvAggregateGroup> output = groups.entrySet().stream()
                .map(entry -> new CsvAggregateGroup(entry.getKey(), entry.getValue().sum,
                        entry.getValue().count))
                .toList();
        return new CsvAggregateResponse(fileId, groupBy, sumColumn, byMonth, matched, total, output);
    }

    private static int columnIndex(CsvData data, String column) {
        int index = column == null ? -1 : data.headers().indexOf(column);
        if (index < 0) {
            throw new CsvInputException("表格列不存在：" + column);
        }
        return index;
    }

    private static int filterIndex(CsvData data, String column, String value) {
        if (column == null && value == null) {
            return -1;
        }
        if (column == null || value == null) {
            throw new CsvInputException("筛选列与筛选值必须同时提供。");
        }
        return columnIndex(data, column);
    }

    private static final class Accumulator {
        private BigDecimal sum = BigDecimal.ZERO;
        private int count;

        void add(BigDecimal value) {
            sum = sum.add(value);
            count++;
        }
    }
}
