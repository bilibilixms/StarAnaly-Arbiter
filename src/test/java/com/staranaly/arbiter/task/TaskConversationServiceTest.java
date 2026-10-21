package com.staranaly.arbiter.task;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.csv.CsvFileService;
import com.staranaly.arbiter.model.ModelGateway;
import com.staranaly.arbiter.model.ModelMessage;
import com.staranaly.arbiter.model.ModelReply;
import com.staranaly.arbiter.model.ModelServiceException;
import com.staranaly.arbiter.model.ModelToolCall;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskConversationServiceTest {

    private final TaskConversationState state = mock(TaskConversationState.class);
    private final ModelGateway modelGateway = mock(ModelGateway.class);
    private final ExecutorService executor = mock(ExecutorService.class);
    private final ReadOnlyToolRegistry tools = new ReadOnlyToolRegistry(
            new ObjectMapper(), mock(CsvFileService.class));
    private final TaskConversationService service = new TaskConversationService(state, modelGateway, tools, executor);

    @Test
    void recordsModelFailureAgainstTheCurrentRun() {
        List<ModelMessage> context = List.of(new ModelMessage("user", "继续分析"));
        TaskRunStart started = new TaskRunStart(7L, context, new TaskRunResponse(
                7L, 42L, TaskStatus.RUNNING, "用户消息，4 字符", null, Instant.now(), null));
        when(state.begin(42L, "继续分析")).thenReturn(started);
        when(modelGateway.next(anyList(), anyBoolean(), any(Duration.class))).thenThrow(
                new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型服务返回 HTTP 502。"));

        assertThatThrownBy(() -> service.send(42L, "继续分析"))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("模型服务返回 HTTP 502。");

        verify(state).fail(7L, "模型服务返回 HTTP 502。");
    }

    @Test
    void marksRunFailedWhenExecutionCannotBeScheduled() {
        TaskRunStart started = new TaskRunStart(7L, List.of(), new TaskRunResponse(
                7L, 42L, TaskStatus.RUNNING, "用户消息，4 字符", null, Instant.now(), null));
        when(state.begin(42L, "继续分析")).thenReturn(started);
        doThrow(new RejectedExecutionException()).when(executor).execute(any(Runnable.class));

        assertThatThrownBy(() -> service.start(42L, "继续分析"))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("任务执行线程不可用。");

        verify(state).fail(7L, "任务执行线程不可用。");
    }

    @Test
    void recordsToolRequestResultAndFinalReply() {
        TaskRunStart started = new TaskRunStart(7L, List.of(new ModelMessage("user", "2+3 等于几")),
                new TaskRunResponse(7L, 42L, TaskStatus.RUNNING, "用户消息，6 字符", null,
                        Instant.now(), null));
        when(state.begin(42L, "2+3 等于几")).thenReturn(started);
        when(modelGateway.next(anyList(), anyBoolean(), any(Duration.class))).thenReturn(
                new ModelReply(null, List.of(new ModelToolCall("call_1", "sum_two_numbers",
                        "{\"a\":2,\"b\":3}"))),
                new ModelReply("结果是 5。", List.of()));

        service.send(42L, "2+3 等于几");

        verify(state).beginStep(7L, 2, TaskRunStepKind.TOOL_CALL,
                "sum_two_numbers", "模型请求工具 sum_two_numbers");
        verify(state).beginStep(7L, 3, TaskRunStepKind.TOOL_RESULT,
                "sum_two_numbers", "整数加法：a=2，b=3");
        verify(state).beginStep(7L, 4, TaskRunStepKind.MODEL_CALL,
                null, "提交工具结果并请求模型回复");
        verify(state).succeed(7L, "2+3 等于几", "结果是 5。", 5);
        verify(modelGateway).next(argThat(messages -> messages.stream()
                .anyMatch(message -> message.role().equals("tool")
                        && message.content().equals("5")
                        && message.toolCallId().equals("call_1"))), eq(false), any(Duration.class));
    }

    @Test
    void rejectsUnknownToolAndRecordsFailure() {
        TaskRunStart started = new TaskRunStart(7L, List.of(new ModelMessage("user", "执行工具")),
                new TaskRunResponse(7L, 42L, TaskStatus.RUNNING, "用户消息，4 字符", null,
                        Instant.now(), null));
        when(state.begin(42L, "执行工具")).thenReturn(started);
        when(modelGateway.next(anyList(), anyBoolean(), any(Duration.class))).thenReturn(
                new ModelReply(null, List.of(new ModelToolCall("call_1", "execute_shell", "{}"))));

        assertThatThrownBy(() -> service.send(42L, "执行工具"))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("模型请求了未授权的工具。");

        verify(state).beginStep(7L, 2, TaskRunStepKind.TOOL_CALL,
                "execute_shell", "模型请求工具 execute_shell");
        verify(state).fail(7L, "模型请求了未授权的工具。");
    }

    @Test
    void rejectsTooManyToolCallsBeforeRunningThem() {
        TaskRunStart started = new TaskRunStart(7L, List.of(new ModelMessage("user", "计算")),
                new TaskRunResponse(7L, 42L, TaskStatus.RUNNING, "用户消息，2 字符", null,
                        Instant.now(), null));
        when(state.begin(42L, "计算")).thenReturn(started);
        when(modelGateway.next(anyList(), anyBoolean(), any(Duration.class))).thenReturn(new ModelReply(null,
                List.of(new ModelToolCall("call_1", "sum_two_numbers", "{\"a\":1,\"b\":2}"),
                        new ModelToolCall("call_2", "sum_two_numbers", "{\"a\":3,\"b\":4}"),
                        new ModelToolCall("call_3", "sum_two_numbers", "{\"a\":5,\"b\":6}"))));

        assertThatThrownBy(() -> service.send(42L, "计算"))
                .isInstanceOf(ModelServiceException.class)
                .hasMessage("工具调用次数超过限制。");

        verify(state).fail(7L, "工具调用次数超过限制。");
    }
}
