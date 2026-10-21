package com.staranaly.arbiter.model;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleModelGateway implements ModelGateway {

    private static final int MAX_RESPONSE_CHARS = 1_000_000;
    private static final int MAX_ARGUMENT_CHARS = 2_000;
    private static final List<Map<String, Object>> TOOLS = List.of(Map.of(
            "type", "function",
            "function", Map.of(
                    "name", "sum_two_numbers",
                    "description", "计算两个整数的和。涉及两个整数加法时调用此工具。",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of("a", Map.of("type", "integer"),
                                    "b", Map.of("type", "integer")),
                            "required", List.of("a", "b"),
                            "additionalProperties", false))), Map.of(
            "type", "function",
            "function", Map.of(
                    "name", "summarize_csv",
                    "description", "对任务上传的 CSV 或 Excel 表格按列分组求和；日期列可按月汇总。",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "fileId", Map.of("type", "integer"),
                                    "groupBy", Map.of("type", "string"),
                                    "sumColumn", Map.of("type", "string"),
                                    "byMonth", Map.of("type", "boolean"),
                                    "filterColumn", Map.of("type", "string"),
                                    "filterValue", Map.of("type", "string")),
                            "required", List.of("fileId", "groupBy", "sumColumn", "byMonth"),
                            "additionalProperties", false))), Map.of(
            "type", "function",
            "function", Map.of(
                    "name", "search_knowledge",
                    "description", "检索当前任务上传的 TXT、Markdown、PDF 或 Word 资料，返回文字片段和来源行号。回答资料问题前调用。",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "query", Map.of("type", "string"),
                                    "topK", Map.of("type", "integer")),
                            "required", List.of("query"),
                            "additionalProperties", false))));

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String apiKey;
    private final String model;

    public OpenAiCompatibleModelGateway(
            ObjectMapper objectMapper,
            @Value("${staranaly.model.base-url}") String baseUrl,
            @Value("${staranaly.model.api-key}") String apiKey,
            @Value("${staranaly.model.name}") String model
    ) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = objectMapper;
        this.endpoint = URI.create(baseUrl.strip().replaceAll("/+$", "") + "/chat/completions");
        this.apiKey = apiKey.strip();
        this.model = model.strip();
    }

    @Override
    public ModelReply next(List<ModelConversationMessage> messages, boolean allowTools, Duration timeout) {
        if (apiKey.isBlank()) {
            throw new ModelServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "模型服务尚未配置 MODEL_API_KEY 或 DEEPSEEK_API_KEY。");
        }
        if (model.isBlank()) {
            throw new ModelServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "模型服务尚未配置 MODEL_NAME。");
        }

        String requestBody;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", messages.stream().map(ModelConversationMessage::toRequest).toList());
            if (allowTools) {
                payload.put("tools", TOOLS);
                payload.put("tool_choice", "auto");
            } else if (messages.stream().anyMatch(message -> message.toolCalls() != null)) {
                payload.put("tools", TOOLS);
                payload.put("tool_choice", "none");
            }
            if (model.startsWith("deepseek-")) {
                payload.put("thinking", Map.of("type", "disabled"));
            }
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "无法构造模型请求。");
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型请求被中断。");
        } catch (IOException exception) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "无法连接模型服务。");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY,
                    "模型服务返回 HTTP " + response.statusCode() + "。");
        }
        if (response.body().length() > MAX_RESPONSE_CHARS) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型响应超过当前长度限制。");
        }

        try {
            JsonNode message = objectMapper.readTree(response.body()).path("choices").path(0).path("message");
            if (!message.isObject()) {
                throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型回复缺少消息对象。");
            }
            JsonNode rawCalls = message.path("tool_calls");
            List<ModelToolCall> calls = new ArrayList<>();
            if (!rawCalls.isMissingNode() && !rawCalls.isNull()) {
                if (!rawCalls.isArray() || rawCalls.size() > 2) {
                    throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型工具请求数量无效。");
                }
                for (JsonNode call : rawCalls) {
                    String id = text(call.path("id"));
                    String name = text(call.path("function").path("name"));
                    String arguments = text(call.path("function").path("arguments"));
                    if (!"function".equals(text(call.path("type"))) || id == null || id.length() > 100
                            || name == null || name.length() > 100 || arguments == null
                            || arguments.length() > MAX_ARGUMENT_CHARS) {
                        throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型工具请求格式无效。");
                    }
                    calls.add(new ModelToolCall(id, name, arguments));
                }
            }
            if (!calls.isEmpty()) {
                String toolMessageContent = text(message.path("content"));
                if (toolMessageContent != null && toolMessageContent.length() > 10000) {
                    throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型回复超过当前长度限制。");
                }
                return new ModelReply(toolMessageContent, List.copyOf(calls));
            }
            String answer = text(message.path("content"));
            if (answer == null) {
                throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型服务未返回文本回复。");
            }
            if (answer.length() > 10000) {
                throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型回复超过当前长度限制。");
            }
            return new ModelReply(answer.strip(), List.of());
        } catch (JsonProcessingException exception) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "无法读取模型回复。");
        }
    }

    private static String text(JsonNode value) {
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }
}
