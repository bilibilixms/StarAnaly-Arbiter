package com.staranaly.arbiter.knowledge;

import java.time.Instant;

public record KnowledgeDocumentMetadata(
        Long id, Long taskId, String name, int byteSize, int chunkCount, Instant createdAt
) {
}
