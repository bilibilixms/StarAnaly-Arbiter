package com.staranaly.arbiter.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.staranaly.arbiter.model.ModelMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskConversationState {

    private final TaskRepository taskRepository;
    private final TaskMessageRepository messageRepository;

    public TaskConversationState(TaskRepository taskRepository, TaskMessageRepository messageRepository) {
        this.taskRepository = taskRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public List<ModelMessage> begin(Long taskId, String content) {
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
        return List.copyOf(context);
    }

    @Transactional
    public TaskMessageResponse succeed(Long taskId, String content, String answer) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        messageRepository.save(new TaskMessage(task, "USER", content));
        TaskMessage reply = messageRepository.save(new TaskMessage(task, "ASSISTANT", answer));
        task.markSucceeded();
        return TaskMessageResponse.from(reply);
    }

    @Transactional
    public void fail(Long taskId, String reason) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
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
}
