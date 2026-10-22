package com.staranaly.arbiter.task;

import java.time.LocalDateTime;

public interface TaskSearchCandidate {
    Long getId();

    String getTitle();

    LocalDateTime getCreatedAt();
}
