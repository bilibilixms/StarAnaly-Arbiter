package com.staranaly.arbiter.task;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.staranaly.arbiter.model.ModelConversationMessage;
import com.staranaly.arbiter.model.ModelGateway;
import com.staranaly.arbiter.model.ModelReply;
import com.staranaly.arbiter.model.ModelServiceException;
import com.staranaly.arbiter.model.ModelToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskConversationService {

    private static final Logger log = LoggerFactory.getLogger(TaskConversationService.class);
    private static final int MAX_MODEL_ROUNDS = 3;
    private static final int MAX_TOOL_CALLS = 2;
    private static final Duration MAX_RUN_TIME = Duration.ofSeconds(120);
    private static final Duration MAX_MODEL_WAIT = Duration.ofSeconds(90);

    private final TaskConversationState state;
    private final ModelGateway modelGateway;
    private final ReadOnlyToolRegistry tools;
    private final ExecutorService executor;

    public TaskConversationService(TaskConversationState state, ModelGateway modelGateway, ReadOnlyToolRegistry tools,
            ExecutorService taskRunExecutor) {
        this.state = state;
        this.modelGateway = modelGateway;
        this.tools = tools;
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
            return runConversation(started, content);
        } catch (ModelServiceException exception) {
            state.fail(started.runId(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            ModelServiceException failure = new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型服务调用失败。");
            state.fail(started.runId(), failure.getMessage());
            throw failure;
        }
    }

    private TaskMessageResponse runConversation(TaskRunStart started, String content) {
        long deadline = System.nanoTime() + MAX_RUN_TIME.toNanos();
        List<ModelConversationMessage> messages = new ArrayList<>();
        started.context().forEach(message -> messages.add(ModelConversationMessage.text(
                message.role(), message.content())));
        Set<String> usedCallIds = new HashSet<>();
        int stepNumber = 1;
        int toolCount = 0;
        for (int round = 1; round <= MAX_MODEL_ROUNDS; round++) {
            boolean allowTools = round == 1;
            ModelReply reply = modelGateway.next(messages, allowTools, remaining(deadline));
            remaining(deadline);
            if (reply.toolCalls().isEmpty()) {
                if (reply.content() == null || reply.content().isBlank()) {
                    throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型服务未返回文本回复。");
                }
                state.completeStep(started.runId(), stepNumber,
                        "模型返回 " + reply.content().codePointCount(0, reply.content().length()) + " 字符");
                return state.succeed(started.runId(), content, reply.content(), stepNumber + 1);
            }
            if (!allowTools || toolCount + reply.toolCalls().size() > MAX_TOOL_CALLS) {
                throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "工具调用次数超过限制。");
            }
            for (ModelToolCall call : reply.toolCalls()) {
                if (!usedCallIds.add(call.id())) {
                    throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型返回重复的工具调用编号。");
                }
            }
            state.completeStep(started.runId(), stepNumber,
                    "模型请求 " + reply.toolCalls().size() + " 个工具");
            messages.add(ModelConversationMessage.assistantTools(reply.content(), reply.toolCalls()));
            for (ModelToolCall call : reply.toolCalls()) {
                remaining(deadline);
                stepNumber++;
                state.beginStep(started.runId(), stepNumber, TaskRunStepKind.TOOL_CALL,
                        call.name(), "模型请求工具 " + call.name());
                ToolResult result = tools.execute(call, started.response().taskId(), started.runId());
                state.completeStep(started.runId(), stepNumber, "参数校验通过");
                stepNumber++;
                state.beginStep(started.runId(), stepNumber, TaskRunStepKind.TOOL_RESULT,
                        call.name(), result.inputSummary());
                state.completeStep(started.runId(), stepNumber, result.outputSummary());
                messages.add(ModelConversationMessage.toolResult(call.id(), result.content()));
                toolCount++;
            }
            stepNumber++;
            state.beginStep(started.runId(), stepNumber, TaskRunStepKind.MODEL_CALL,
                    null, "提交工具结果并请求模型回复");
        }
        throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "模型调用轮次超过限制。");
    }

    private static Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) {
            throw new ModelServiceException(HttpStatus.BAD_GATEWAY, "任务执行超过 120 秒限制。");
        }
        return Duration.ofNanos(Math.min(nanos, MAX_MODEL_WAIT.toNanos()));
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
