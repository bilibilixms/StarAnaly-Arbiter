package com.staranaly.arbiter.task;

import java.util.List;

public record SaveCreativeBriefRequest(
        String objective,
        String audience,
        String coreMessage,
        String tone,
        List<CreativeBrief.Fact> facts,
        List<String> unknowns,
        List<CreativeBrief.Direction> directions,
        boolean confirmed
) {
    public CreativeBrief content() {
        return new CreativeBrief(objective, audience, coreMessage, tone, facts, unknowns, directions);
    }
}
