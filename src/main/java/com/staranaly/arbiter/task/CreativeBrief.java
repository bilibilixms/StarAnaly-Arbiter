package com.staranaly.arbiter.task;

import java.util.List;

public record CreativeBrief(
        String objective,
        String audience,
        String coreMessage,
        String tone,
        List<Fact> facts,
        List<String> unknowns,
        List<Direction> directions
) {
    public record Fact(String text, String source) {
    }

    public record Direction(String name, String angle, String headlineIdea) {
    }
}
