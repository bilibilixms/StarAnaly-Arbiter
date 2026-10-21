package com.staranaly.arbiter.task;

import java.util.List;

import com.staranaly.arbiter.model.ModelMessage;

public record TaskRunStart(Long runId, List<ModelMessage> context, TaskRunResponse response) {
}
