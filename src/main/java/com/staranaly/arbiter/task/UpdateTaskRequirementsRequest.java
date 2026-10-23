package com.staranaly.arbiter.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTaskRequirementsRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description
) {
}
