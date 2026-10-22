package com.staranaly.arbiter.share;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class XiaohongshuExceptionHandler {

    @ExceptionHandler(XiaohongshuInputException.class)
    public ResponseEntity<ProblemDetail> invalidInput(XiaohongshuInputException exception) {
        return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }
}
