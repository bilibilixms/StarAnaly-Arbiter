package com.staranaly.arbiter.csv;

import java.math.BigDecimal;

public record CsvAggregateGroup(String key, BigDecimal sum, int rowCount) {
}
