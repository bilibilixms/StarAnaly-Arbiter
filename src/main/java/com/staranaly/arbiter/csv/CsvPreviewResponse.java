package com.staranaly.arbiter.csv;

import java.util.List;
import java.util.Map;

public record CsvPreviewResponse(CsvFileMetadata file, List<Map<String, String>> rows) {
}
