package com.staranaly.arbiter.task;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.csv.CsvAggregateGroup;
import com.staranaly.arbiter.csv.CsvAggregateResponse;
import com.staranaly.arbiter.csv.CsvFileService;
import com.staranaly.arbiter.knowledge.KnowledgeHit;
import com.staranaly.arbiter.knowledge.KnowledgeSearchResponse;
import com.staranaly.arbiter.knowledge.KnowledgeService;
import com.staranaly.arbiter.model.ModelServiceException;
import com.staranaly.arbiter.model.ModelToolCall;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadOnlyToolRegistryTest {

    private final CsvFileService csvFiles = mock(CsvFileService.class);
    private final KnowledgeService knowledge = mock(KnowledgeService.class);
    private final ReadOnlyToolRegistry tools = new ReadOnlyToolRegistry(new ObjectMapper(), csvFiles, knowledge);

    @Test
    void addsTwoBoundedIntegers() {
        ToolResult result = tools.execute(new ModelToolCall("call_1", "sum_two_numbers",
                "{\"a\":2,\"b\":3}"), 42L, 7L);

        assertThat(result.content()).isEqualTo("5");
        assertThat(result.inputSummary()).contains("a=2", "b=3");
    }

    @Test
    void rejectsUnknownToolAndUnexpectedArguments() {
        assertThatThrownBy(() -> tools.execute(new ModelToolCall("call_1", "execute_shell", "{}"), 42L, 7L))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("模型请求了未授权的工具。");
        assertThatThrownBy(() -> tools.execute(new ModelToolCall("call_2", "sum_two_numbers",
                "{\"a\":2,\"b\":3,\"command\":\"ls\"}"), 42L, 7L))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("模型工具参数无效。");
        assertThatThrownBy(() -> tools.execute(new ModelToolCall("call_3", "sum_two_numbers",
                "{\"a\":1000001,\"b\":3}"), 42L, 7L))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("模型工具参数无效。");
        assertThatThrownBy(() -> tools.execute(new ModelToolCall("call_4", "sum_two_numbers",
                "{\"a\":1,\"a\":2,\"b\":3}"), 42L, 7L))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("模型工具参数不是有效 JSON。");
    }

    @Test
    void savesCsvAnalysisForTheCurrentRun() {
        CsvAggregateResponse analysis = new CsvAggregateResponse(3L, "date", "amount", true,
                4, new BigDecimal("500"), List.of(
                        new CsvAggregateGroup("2026-01", new BigDecimal("300"), 2),
                        new CsvAggregateGroup("2026-02", new BigDecimal("200"), 2)));
        when(csvFiles.aggregate(42L, 3L, "date", "amount", true, null, null)).thenReturn(analysis);

        ToolResult result = tools.execute(new ModelToolCall("call_1", "summarize_csv",
                "{\"fileId\":3,\"groupBy\":\"date\",\"sumColumn\":\"amount\",\"byMonth\":true}"),
                42L, 7L);

        assertThat(result.content()).contains("2026-01", "300", "2026-02", "200");
        verify(csvFiles).saveAnalysis(7L, 3L, analysis);
    }

    @Test
    void searchesKnowledgeForCurrentTaskAndRun() {
        KnowledgeSearchResponse matches = new KnowledgeSearchResponse("退货期限", List.of(
                new KnowledgeHit(11L, 3L, "规则.md", 2, 3, "签收日起 14 天内", 2)));
        when(knowledge.searchForTool(42L, 7L, "退货期限", 2)).thenReturn(matches);

        ToolResult result = tools.execute(new ModelToolCall("call_2", "search_knowledge",
                "{\"query\":\"退货期限\",\"topK\":2}"), 42L, 7L);

        assertThat(result.content()).contains("规则.md", "14 天", "startLine");
        verify(knowledge).searchForTool(42L, 7L, "退货期限", 2);
    }

    @Test
    void rejectsUnexpectedKnowledgeArguments() {
        assertThatThrownBy(() -> tools.execute(new ModelToolCall("call_2", "search_knowledge",
                "{\"query\":\"退货期限\",\"fileId\":3}"), 42L, 7L))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("模型资料检索参数无效。");
    }
}
