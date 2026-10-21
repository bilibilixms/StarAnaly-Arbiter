package com.staranaly.arbiter.csv;

import java.time.Instant;

public record CsvAnalysisResult(Long id, Long runId, CsvAggregateResponse result, Instant createdAt) {
}
