package com.staranaly.arbiter.task;

import java.time.Instant;
import java.time.ZoneOffset;

public record TaskRunResponse(
        Long id,
        Long taskId,
        TaskStatus status,
        String requestSummary,
        String lastError,
        Instant startedAt,
        Instant finishedAt
) {
    public static TaskRunResponse from(TaskRun run) {
        return new TaskRunResponse(
                run.getId(),
                run.getTaskId(),
                run.getStatus(),
                run.getRequestSummary(),
                run.getLastError(),
                run.getStartedAt().toInstant(ZoneOffset.UTC),
                run.getFinishedAt() == null ? null : run.getFinishedAt().toInstant(ZoneOffset.UTC)
        );
    }
}
