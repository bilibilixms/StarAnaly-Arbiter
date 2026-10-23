package com.staranaly.arbiter.task;

import java.time.Instant;
import java.util.List;

public record CreativeBriefResponse(
        String objective,
        String audience,
        String coreMessage,
        String tone,
        List<CreativeBrief.Fact> facts,
        List<String> unknowns,
        List<CreativeBrief.Direction> directions,
        boolean confirmed,
        boolean sourcesChanged,
        Instant updatedAt
) {
    public static CreativeBriefResponse from(CreativeBrief brief, boolean confirmed,
            boolean sourcesChanged, Instant updatedAt) {
        return new CreativeBriefResponse(brief.objective(), brief.audience(), brief.coreMessage(),
                brief.tone(), brief.facts(), brief.unknowns(), brief.directions(),
                confirmed, sourcesChanged, updatedAt);
    }
}
