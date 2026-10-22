package com.staranaly.arbiter.model;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ModelConversationMessage(
        String role, String content, List<ModelToolCall> toolCalls, String toolCallId,
        List<Map<String, Object>> contentParts
) {
    public static ModelConversationMessage text(String role, String content) {
        return new ModelConversationMessage(role, content, null, null, null);
    }

    public static ModelConversationMessage imagePrompt(String prompt, byte[] image, String mimeType) {
        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(image);
        return new ModelConversationMessage("user", null, null, null, List.of(
                Map.of("type", "text", "text", prompt),
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))));
    }

    public static ModelConversationMessage assistantTools(String content, List<ModelToolCall> calls) {
        return new ModelConversationMessage("assistant", content, List.copyOf(calls), null, null);
    }

    public static ModelConversationMessage toolResult(String callId, String result) {
        return new ModelConversationMessage("tool", result, null, callId, null);
    }

    public Map<String, Object> toRequest() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("role", role);
        if (content != null || toolCalls != null || contentParts != null) {
            value.put("content", contentParts != null ? contentParts : content);
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
