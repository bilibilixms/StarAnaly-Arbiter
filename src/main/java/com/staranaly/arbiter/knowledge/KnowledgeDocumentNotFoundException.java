package com.staranaly.arbiter.knowledge;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class KnowledgeDocumentNotFoundException extends RuntimeException {

    public KnowledgeDocumentNotFoundException(Long taskId, Long documentId) {
        super("Knowledge document " + documentId + " was not found for task " + taskId);
    }
}
