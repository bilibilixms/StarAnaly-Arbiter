package com.staranaly.arbiter.task;

import java.time.Instant;
import java.time.ZoneOffset;

public record TaskRunStepResponse(
        Long id,
        int stepNumber,
        TaskRunStepKind kind,
        TaskRunStepStatus status,
        String toolName,
        String inputSummary,
        String outputSummary,
        Instant startedAt,
        Instant finishedAt
) {
    public static TaskRunStepResponse from(TaskRunStep step) {
        return new TaskRunStepResponse(
                step.getId(),
                step.getStepNumber(),
                step.getKind(),
                step.getStatus(),
                step.getToolName(),
                step.getInputSummary(),
                step.getOutputSummary(),
                step.getStartedAt().toInstant(ZoneOffset.UTC),
                step.getFinishedAt() == null ? null : step.getFinishedAt().toInstant(ZoneOffset.UTC)
        );
    }
}
