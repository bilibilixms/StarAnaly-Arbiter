package com.staranaly.arbiter.csv;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvAnalyzerTest {

    private final CsvParser parser = new CsvParser();
    private final CsvAnalyzer analyzer = new CsvAnalyzer();

    @Test
    void calculatesMonthlySalesAndFiltersRows() {
        CsvData data = parser.parse("""
                date,category,amount
                2026-01-03,A,100
                2026-01-18,B,200
                2026-02-02,A,80
                2026-02-12,B,120
                """.getBytes(StandardCharsets.UTF_8));

        CsvAggregateResponse result = analyzer.aggregate(3L, data, "date", "amount",
                true, null, null);

        assertThat(result.total()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(result.groups()).containsExactly(
                new CsvAggregateGroup("2026-01", new BigDecimal("300"), 2),
                new CsvAggregateGroup("2026-02", new BigDecimal("200"), 2));
        assertThat(analyzer.rows(data, "category", "A", 100)).hasSize(2);
        assertThat(analyzer.aggregate(3L, data, "category", "amount",
                false, "category", "A").total()).isEqualByComparingTo(new BigDecimal("180"));
    }

    @Test
    void rejectsNonNumericAmountsAndMissingColumns() {
        CsvData data = parser.parse("category,amount\nA,not-number\n"
                .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> analyzer.aggregate(1L, data, "category", "amount",
                false, null, null)).isInstanceOf(CsvInputException.class)
                .hasMessageContaining("无效数值");
        assertThatThrownBy(() -> analyzer.aggregate(1L, data, "missing", "amount",
                false, null, null)).isInstanceOf(CsvInputException.class)
                .hasMessageContaining("列不存在");
    }
}
