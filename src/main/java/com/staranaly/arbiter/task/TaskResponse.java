package com.staranaly.arbiter.task;

import java.time.Instant;
import java.time.ZoneOffset;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Instant createdAt,
        String lastError
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getCreatedAt().toInstant(ZoneOffset.UTC),
                task.getLastError()
        );
    }
}
