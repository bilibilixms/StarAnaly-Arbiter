package com.staranaly.arbiter.csv;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CsvFileNotFoundException extends RuntimeException {

    public CsvFileNotFoundException(Long taskId, Long fileId) {
        super("CSV file " + fileId + " was not found for task " + taskId);
    }
}
