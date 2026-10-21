package com.staranaly.arbiter.task;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TaskRunNotFoundException extends RuntimeException {

    public TaskRunNotFoundException(Long taskId, Long runId) {
        super("Task run " + runId + " was not found for task " + taskId);
    }
}
