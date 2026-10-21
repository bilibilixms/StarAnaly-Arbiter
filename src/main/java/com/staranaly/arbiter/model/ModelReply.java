package com.staranaly.arbiter.model;

import java.util.List;

public record ModelReply(String content, List<ModelToolCall> toolCalls) {
}
