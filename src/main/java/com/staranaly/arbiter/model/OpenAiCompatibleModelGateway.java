package com.staranaly.arbiter.model;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    public String complete(List<ModelMessage> messages) {
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
            requestBody = objectMapper.writeValueAsString(Map.of("model", model, "messages", messages));
        } catch (JsonProcessingException exception) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "无法构造模型请求。");
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(90))
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

        try {
            JsonNode content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型服务未返回文本回复。");
            }
            String answer = content.asText().strip();
            if (answer.length() > 10000) {
                throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型回复超过当前长度限制。");
            }
            return answer;
        } catch (JsonProcessingException exception) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "无法读取模型回复。");
        }
    }
}
