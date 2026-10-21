package com.staranaly.arbiter.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ModelConversationMessage(
        String role, String content, List<ModelToolCall> toolCalls, String toolCallId
) {
    public static ModelConversationMessage text(String role, String content) {
        return new ModelConversationMessage(role, content, null, null);
    }

    public static ModelConversationMessage assistantTools(String content, List<ModelToolCall> calls) {
        return new ModelConversationMessage("assistant", content, List.copyOf(calls), null);
    }

    public static ModelConversationMessage toolResult(String callId, String result) {
        return new ModelConversationMessage("tool", result, null, callId);
    }

    public Map<String, Object> toRequest() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("role", role);
        if (content != null || toolCalls != null) {
            value.put("content", content);
        }
        if (toolCalls != null) {
            value.put("tool_calls", toolCalls.stream().map(call -> Map.of(
                    "id", call.id(), "type", "function", "function", Map.of(
                            "name", call.name(), "arguments", call.arguments()))).toList());
        }
        if (toolCallId != null) {
            value.put("tool_call_id", toolCallId);
        }
        return value;
    }
}
