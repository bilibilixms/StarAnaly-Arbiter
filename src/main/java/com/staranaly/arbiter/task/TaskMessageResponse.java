package com.staranaly.arbiter.task;

import java.time.Instant;
import java.time.ZoneOffset;

public record TaskMessageResponse(Long id, String role, String content, Instant createdAt) {

    public static TaskMessageResponse from(TaskMessage message) {
        return new TaskMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }
}
