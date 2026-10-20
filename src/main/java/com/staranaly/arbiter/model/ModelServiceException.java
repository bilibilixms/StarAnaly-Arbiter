package com.staranaly.arbiter.model;

import org.springframework.http.HttpStatus;

public class ModelServiceException extends RuntimeException {

    private final HttpStatus status;

    public ModelServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
