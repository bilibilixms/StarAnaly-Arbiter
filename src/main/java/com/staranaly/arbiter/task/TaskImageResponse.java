package com.staranaly.arbiter.task;

import java.time.Instant;

public record TaskImageResponse(Long id, Long taskId, String name, String mime, int byteSize, Instant createdAt) {
}
