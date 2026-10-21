package com.staranaly.arbiter.task;

import java.util.List;
import java.util.concurrent.ExecutorService;

import com.staranaly.arbiter.model.ModelGateway;
import com.staranaly.arbiter.model.ModelServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskConversationService {

    private static final Logger log = LoggerFactory.getLogger(TaskConversationService.class);

    private final TaskConversationState state;
    private final ModelGateway modelGateway;
    private final ExecutorService executor;

    public TaskConversationService(TaskConversationState state, ModelGateway modelGateway,
            ExecutorService taskRunExecutor) {
        this.state = state;
        this.modelGateway = modelGateway;
        this.executor = taskRunExecutor;
    }

    public TaskMessageResponse send(Long taskId, String content) {
        String normalizedContent = content.trim();
        TaskRunStart started = state.begin(taskId, normalizedContent);
        return execute(started, normalizedContent);
    }

    public TaskRunResponse start(Long taskId, String content) {
        String normalizedContent = content.trim();
        TaskRunStart started = state.begin(taskId, normalizedContent);
        try {
            executor.execute(() -> {
                try {
                    execute(started, normalizedContent);
                } catch (RuntimeException exception) {
                    log.warn("Task run {} ended with an error", started.runId());
                }
            });
        } catch (RuntimeException exception) {
            state.fail(started.runId(), "任务执行线程不可用。");
            throw new ModelServiceException(HttpStatus.SERVICE_UNAVAILABLE, "任务执行线程不可用。");
        }
        return started.response();
    }

    private TaskMessageResponse execute(TaskRunStart started, String content) {
        try {
            String answer = modelGateway.complete(started.context());
            return state.succeed(started.runId(), content, answer);
        } catch (ModelServiceException exception) {
            state.fail(started.runId(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            ModelServiceException failure = new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型服务调用失败。");
            state.fail(started.runId(), failure.getMessage());
            throw failure;
        }
    }

    public List<TaskMessageResponse> list(Long taskId) {
        return state.list(taskId);
    }

    public Page<TaskRunResponse> listRuns(Long taskId, Pageable pageable) {
        return state.listRuns(taskId, pageable);
    }

    public TaskRunResponse findRun(Long taskId, Long runId) {
        return state.findRun(taskId, runId);
    }

    public List<TaskRunStepResponse> listSteps(Long taskId, Long runId) {
        return state.listSteps(taskId, runId);
    }
}
