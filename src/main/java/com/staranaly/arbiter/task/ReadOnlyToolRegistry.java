package com.staranaly.arbiter.task;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.csv.CsvAggregateResponse;
import com.staranaly.arbiter.csv.CsvFileNotFoundException;
import com.staranaly.arbiter.csv.CsvFileService;
import com.staranaly.arbiter.csv.CsvInputException;
import com.staranaly.arbiter.knowledge.KnowledgeInputException;
import com.staranaly.arbiter.knowledge.KnowledgeSearchResponse;
import com.staranaly.arbiter.knowledge.KnowledgeService;
import com.staranaly.arbiter.model.ModelServiceException;
import com.staranaly.arbiter.model.ModelToolCall;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ReadOnlyToolRegistry {

    private static final int MAX_ABS_OPERAND = 1_000_000;

    private final ObjectMapper objectMapper;
    private final CsvFileService csvFiles;
    private final KnowledgeService knowledge;

    public ReadOnlyToolRegistry(ObjectMapper objectMapper, CsvFileService csvFiles,
            KnowledgeService knowledge) {
        this.objectMapper = objectMapper;
        this.csvFiles = csvFiles;
        this.knowledge = knowledge;
    }

    public ToolResult execute(ModelToolCall call, Long taskId, Long runId) {
        if (!"sum_two_numbers".equals(call.name()) && !"summarize_csv".equals(call.name())
                && !"search_knowledge".equals(call.name())) {
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
        if ("summarize_csv".equals(call.name())) {
            return summarizeCsv(arguments, taskId, runId);
        }
        if ("search_knowledge".equals(call.name())) {
            return searchKnowledge(arguments, taskId, runId);
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

    private ToolResult summarizeCsv(JsonNode arguments, Long taskId, Long runId) {
        if (arguments == null || !arguments.isObject()
                || (arguments.size() != 4 && arguments.size() != 6)
                || !arguments.path("fileId").isIntegralNumber()
                || !arguments.path("fileId").canConvertToLong()
                || arguments.path("fileId").longValue() <= 0
                || !validColumn(arguments.path("groupBy"))
                || !validColumn(arguments.path("sumColumn"))
                || !arguments.path("byMonth").isBoolean()
                || (arguments.size() == 6 && (!validColumn(arguments.path("filterColumn"))
                        || !arguments.path("filterValue").isTextual()
                        || arguments.path("filterValue").asText().length() > 200))) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型表格工具参数无效。");
        }
        Long fileId = arguments.path("fileId").longValue();
        String groupBy = arguments.path("groupBy").asText();
        String sumColumn = arguments.path("sumColumn").asText();
        boolean byMonth = arguments.path("byMonth").booleanValue();
        String filterColumn = arguments.size() == 6 ? arguments.path("filterColumn").asText() : null;
        String filterValue = arguments.size() == 6 ? arguments.path("filterValue").asText() : null;
        CsvAggregateResponse result;
        try {
            result = csvFiles.aggregate(taskId, fileId, groupBy, sumColumn,
                    byMonth, filterColumn, filterValue);
        } catch (CsvInputException | CsvFileNotFoundException exception) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型表格工具参数无效或文件不可用。");
        }
        String content;
        try {
            content = objectMapper.writeValueAsString(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "无法编码表格分析结果。");
        }
        if (content.length() > 10_000) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "表格分析结果超过模型输入限制。");
        }
        csvFiles.saveAnalysis(runId, fileId, result);
        return new ToolResult(content,
                "表格文件 " + fileId + "：按 " + groupBy + " 汇总 " + sumColumn,
                "得到 " + result.groups().size() + " 组，总计 " + result.total().toPlainString());
    }

    private ToolResult searchKnowledge(JsonNode arguments, Long taskId, Long runId) {
        if (arguments == null || !arguments.isObject() || arguments.size() < 1 || arguments.size() > 2
                || !arguments.path("query").isTextual()
                || (arguments.size() == 2 && (!arguments.has("topK")
                        || !arguments.path("topK").isIntegralNumber()
                        || !arguments.path("topK").canConvertToInt()))) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型资料检索参数无效。");
        }
        String query = arguments.path("query").asText();
        int topK = arguments.has("topK") ? arguments.path("topK").intValue() : 5;
        KnowledgeSearchResponse result;
        try {
            result = knowledge.searchForTool(taskId, runId, query, topK);
        } catch (KnowledgeInputException exception) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型资料检索参数无效。");
        }
        String content;
        try {
            content = objectMapper.writeValueAsString(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "无法编码资料检索结果。");
        }
        if (content.length() > 10_000) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "资料检索结果超过模型输入限制。");
        }
        return new ToolResult(content, "资料检索：" + query,
                "命中 " + result.hits().size() + " 个片段");
    }

    private static boolean validColumn(JsonNode value) {
        return value.isTextual() && !value.asText().isBlank() && value.asText().length() <= 100;
    }

    private static boolean validInteger(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToInt()
                && Math.abs((long) value.intValue()) <= MAX_ABS_OPERAND;
    }
}
