package com.staranaly.arbiter.csv;

import java.util.List;

public record CsvData(List<String> headers, List<List<String>> rows) {
}
