package com.staranaly.arbiter.task;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class TaskBusyException extends RuntimeException {

    public TaskBusyException(Long id) {
        super("Task " + id + " is already running");
    }
}
