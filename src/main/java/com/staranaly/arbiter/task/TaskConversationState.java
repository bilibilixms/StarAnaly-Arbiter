package com.staranaly.arbiter.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.staranaly.arbiter.model.ModelMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskConversationState {

    private final TaskRepository taskRepository;
    private final TaskMessageRepository messageRepository;
    private final TaskRunRepository runRepository;
    private final TaskRunStepRepository stepRepository;

    public TaskConversationState(TaskRepository taskRepository, TaskMessageRepository messageRepository,
            TaskRunRepository runRepository, TaskRunStepRepository stepRepository) {
        this.taskRepository = taskRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
    }

    @Transactional
    public TaskRunStart begin(Long taskId, String content) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        if (task.getStatus() == TaskStatus.RUNNING) {
            throw new TaskBusyException(taskId);
        }

        List<ModelMessage> context = new ArrayList<>();
        context.add(new ModelMessage("system", "你是星析项目中的任务助手。请根据任务信息和对话记录回答用户。"));
        context.add(new ModelMessage("user", "任务标题：" + task.getTitle()
                + "\n任务说明：" + (task.getDescription() == null ? "无" : task.getDescription())));

        List<TaskMessage> recent = new ArrayList<>(messageRepository.findTop20ByTaskIdOrderByIdDesc(taskId));
        Collections.reverse(recent);
        for (TaskMessage message : recent) {
            context.add(new ModelMessage(message.getRole().toLowerCase(Locale.ROOT), message.getContent()));
        }
        context.add(new ModelMessage("user", content));

        task.markRunning();
        TaskRun run = runRepository.save(new TaskRun(task, content));
        stepRepository.save(new TaskRunStep(run, 1, TaskRunStepKind.MODEL_CALL, "请求模型生成回复"));
        return new TaskRunStart(run.getId(), List.copyOf(context), TaskRunResponse.from(run));
    }

    @Transactional
    public TaskMessageResponse succeed(Long runId, String content, String answer) {
        TaskRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalStateException("Task run disappeared: " + runId));
        if (run.getStatus() != TaskStatus.RUNNING) {
            throw new IllegalStateException("Task run is no longer running: " + runId);
        }
        Task task = taskRepository.findByIdForUpdate(run.getTaskId())
                .orElseThrow(() -> new IllegalStateException("Task disappeared for run: " + runId));
        messageRepository.save(new TaskMessage(task, "USER", content));
        TaskMessage reply = messageRepository.save(new TaskMessage(task, "ASSISTANT", answer));
        TaskRunStep modelStep = stepRepository.findByRun_IdAndStepNumber(runId, 1)
                .orElseThrow(() -> new IllegalStateException("Model step disappeared for run: " + runId));
        modelStep.markSucceeded("模型返回 " + answer.codePointCount(0, answer.length()) + " 字符");
        TaskRunStep finalStep = new TaskRunStep(run, 2, TaskRunStepKind.FINAL_ANSWER, "保存最终回复");
        finalStep.markSucceeded("回复已保存");
        stepRepository.save(finalStep);
        run.markSucceeded();
        task.markSucceeded();
        return TaskMessageResponse.from(reply);
    }

    @Transactional
    public void fail(Long runId, String reason) {
        TaskRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalStateException("Task run disappeared: " + runId));
        if (run.getStatus() != TaskStatus.RUNNING) {
            return;
        }
        Task task = taskRepository.findByIdForUpdate(run.getTaskId())
                .orElseThrow(() -> new IllegalStateException("Task disappeared for run: " + runId));
        for (TaskRunStep step : stepRepository.findByRun_IdAndStatus(runId, TaskRunStepStatus.RUNNING)) {
            step.markFailed(reason);
        }
        run.markFailed(reason);
        task.markFailed(reason);
    }

    @Transactional(readOnly = true)
    public List<TaskMessageResponse> list(Long taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new TaskNotFoundException(taskId);
        }
        return messageRepository.findByTaskIdOrderByIdAsc(taskId).stream()
                .map(TaskMessageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TaskRunResponse> listRuns(Long taskId, Pageable pageable) {
        if (!taskRepository.existsById(taskId)) {
            throw new TaskNotFoundException(taskId);
        }
        return runRepository.findByTask_Id(taskId, pageable).map(TaskRunResponse::from);
    }

    @Transactional(readOnly = true)
    public TaskRunResponse findRun(Long taskId, Long runId) {
        return TaskRunResponse.from(requireRun(taskId, runId));
    }

    @Transactional(readOnly = true)
    public List<TaskRunStepResponse> listSteps(Long taskId, Long runId) {
        requireRun(taskId, runId);
        return stepRepository.findByRun_IdOrderByStepNumberAsc(runId).stream()
                .map(TaskRunStepResponse::from)
                .toList();
    }

    private TaskRun requireRun(Long taskId, Long runId) {
        return runRepository.findByIdAndTask_Id(runId, taskId)
                .orElseThrow(() -> new TaskRunNotFoundException(taskId, runId));
    }
}
