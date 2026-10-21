package com.staranaly.arbiter.csv;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class CsvUploadExceptionHandler {

    @ExceptionHandler(CsvInputException.class)
    public ResponseEntity<ProblemDetail> invalidCsv(CsvInputException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                exception.getMessage());
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(CsvFileNotFoundException.class)
    public ResponseEntity<ProblemDetail> missingFile(CsvFileNotFoundException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> tooLarge(MaxUploadSizeExceededException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "CSV 文件不能超过 5 MiB。");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(detail);
    }
}
