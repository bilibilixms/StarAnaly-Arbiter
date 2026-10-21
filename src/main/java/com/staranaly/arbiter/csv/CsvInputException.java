package com.staranaly.arbiter.csv;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CsvInputException extends RuntimeException {

    public CsvInputException(String message) {
        super(message);
    }
}
