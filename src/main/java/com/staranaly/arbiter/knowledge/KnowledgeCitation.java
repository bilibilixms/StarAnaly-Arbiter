package com.staranaly.arbiter.knowledge;

public record KnowledgeCitation(
        Long id, Long documentId, String documentName,
        int startLine, int endLine, String excerpt
) {
}
