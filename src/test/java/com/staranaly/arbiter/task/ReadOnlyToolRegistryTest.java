package com.staranaly.arbiter.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.model.ModelServiceException;
import com.staranaly.arbiter.model.ModelToolCall;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadOnlyToolRegistryTest {

    private final ReadOnlyToolRegistry tools = new ReadOnlyToolRegistry(new ObjectMapper());

    @Test
    void addsTwoBoundedIntegers() {
        ToolResult result = tools.execute(new ModelToolCall("call_1", "sum_two_numbers",
                "{\"a\":2,\"b\":3}"));

        assertThat(result.content()).isEqualTo("5");
        assertThat(result.inputSummary()).contains("a=2", "b=3");
    }

    @Test
    void rejectsUnknownToolAndUnexpectedArguments() {
        assertThatThrownBy(() -> tools.execute(new ModelToolCall("call_1", "execute_shell", "{}")))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("模型请求了未授权的工具。");
        assertThatThrownBy(() -> tools.execute(new ModelToolCall("call_2", "sum_two_numbers",
                "{\"a\":2,\"b\":3,\"command\":\"ls\"}")))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("模型工具参数无效。");
        assertThatThrownBy(() -> tools.execute(new ModelToolCall("call_3", "sum_two_numbers",
                "{\"a\":1000001,\"b\":3}")))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("模型工具参数无效。");
        assertThatThrownBy(() -> tools.execute(new ModelToolCall("call_4", "sum_two_numbers",
                "{\"a\":1,\"a\":2,\"b\":3}")))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("模型工具参数不是有效 JSON。");
    }
}
