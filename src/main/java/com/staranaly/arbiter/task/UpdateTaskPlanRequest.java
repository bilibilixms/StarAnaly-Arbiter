package com.staranaly.arbiter.task;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTaskPlanRequest(
        @NotNull TaskWorkflowStage workflowStage,
        @Size(max = 5000) String discussionSummary
) {
}
