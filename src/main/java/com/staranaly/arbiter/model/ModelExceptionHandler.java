package com.staranaly.arbiter.model;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ModelExceptionHandler {

    @ExceptionHandler(ModelServiceException.class)
    public ResponseEntity<ProblemDetail> handle(ModelServiceException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(detail);
    }
}
