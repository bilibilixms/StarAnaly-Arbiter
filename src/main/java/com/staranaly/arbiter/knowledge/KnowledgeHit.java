package com.staranaly.arbiter.knowledge;

public record KnowledgeHit(
        Long chunkId, Long documentId, String documentName,
        int startLine, int endLine, String excerpt, int score
) {
}
