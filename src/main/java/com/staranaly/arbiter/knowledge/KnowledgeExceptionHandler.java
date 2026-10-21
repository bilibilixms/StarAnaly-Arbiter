package com.staranaly.arbiter.knowledge;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class KnowledgeExceptionHandler {

    @ExceptionHandler(KnowledgeInputException.class)
    public ResponseEntity<ProblemDetail> invalid(KnowledgeInputException exception) {
        return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(KnowledgeDocumentNotFoundException.class)
    public ResponseEntity<ProblemDetail> missing(KnowledgeDocumentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage()));
    }
}
