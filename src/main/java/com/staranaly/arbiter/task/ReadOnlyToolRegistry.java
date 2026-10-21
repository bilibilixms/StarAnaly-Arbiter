package com.staranaly.arbiter.task;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.model.ModelServiceException;
import com.staranaly.arbiter.model.ModelToolCall;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ReadOnlyToolRegistry {

    private static final int MAX_ABS_OPERAND = 1_000_000;

    private final ObjectMapper objectMapper;

    public ReadOnlyToolRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ToolResult execute(ModelToolCall call) {
        if (!"sum_two_numbers".equals(call.name())) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型请求了未授权的工具。");
        }
        JsonNode arguments;
        try (JsonParser parser = objectMapper.getFactory().createParser(call.arguments())) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            arguments = objectMapper.readTree(parser);
            if (parser.nextToken() != null) {
                throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型工具参数无效。");
            }
        } catch (IOException exception) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型工具参数不是有效 JSON。");
        }
        if (arguments == null || !arguments.isObject() || arguments.size() != 2
                || !validInteger(arguments.path("a")) || !validInteger(arguments.path("b"))) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型工具参数无效。");
        }
        int a = arguments.path("a").intValue();
        int b = arguments.path("b").intValue();
        String result = Integer.toString(a + b);
        return new ToolResult(result, "整数加法：a=" + a + "，b=" + b, "计算结果：" + result);
    }

    private static boolean validInteger(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToInt()
                && Math.abs((long) value.intValue()) <= MAX_ABS_OPERAND;
    }
}
