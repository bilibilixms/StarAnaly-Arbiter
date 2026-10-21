package com.staranaly.arbiter.task;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import com.staranaly.arbiter.model.ModelGateway;
import com.staranaly.arbiter.model.ModelMessage;
import com.staranaly.arbiter.model.ModelServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskConversationServiceTest {

    private final TaskConversationState state = mock(TaskConversationState.class);
    private final ModelGateway modelGateway = mock(ModelGateway.class);
    private final ExecutorService executor = mock(ExecutorService.class);
    private final TaskConversationService service = new TaskConversationService(state, modelGateway, executor);

    @Test
    void recordsModelFailureAgainstTheCurrentRun() {
        List<ModelMessage> context = List.of(new ModelMessage("user", "继续分析"));
        TaskRunStart started = new TaskRunStart(7L, context, new TaskRunResponse(
                7L, 42L, TaskStatus.RUNNING, "用户消息，4 字符", null, Instant.now(), null));
        when(state.begin(42L, "继续分析")).thenReturn(started);
        when(modelGateway.complete(context)).thenThrow(
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
}
