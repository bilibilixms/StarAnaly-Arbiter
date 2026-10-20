package com.staranaly.arbiter.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendTaskMessageRequest(@NotBlank @Size(max = 5000) String content) {
}
