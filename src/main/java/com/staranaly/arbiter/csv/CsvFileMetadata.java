package com.staranaly.arbiter.csv;

import java.time.Instant;
import java.util.List;

public record CsvFileMetadata(
        Long id, Long taskId, String name, int byteSize, int rowCount,
        List<String> headers, Instant createdAt
) {
}
