package com.staranaly.arbiter.knowledge;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class KnowledgeInputException extends RuntimeException {

    public KnowledgeInputException(String message) {
        super(message);
    }
}
