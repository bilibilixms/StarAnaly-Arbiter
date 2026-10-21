package com.staranaly.arbiter.csv;

import java.math.BigDecimal;
import java.util.List;

public record CsvAggregateResponse(
        Long fileId, String groupBy, String sumColumn, boolean byMonth,
        int matchedRows, BigDecimal total, List<CsvAggregateGroup> groups
) {
}
