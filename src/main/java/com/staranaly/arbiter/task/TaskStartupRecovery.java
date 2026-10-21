package com.staranaly.arbiter.task;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskStartupRecovery {

    private final TaskRepository repository;
    private final TaskRunRepository runRepository;
    private final TaskRunStepRepository stepRepository;

    public TaskStartupRecovery(TaskRepository repository, TaskRunRepository runRepository,
            TaskRunStepRepository stepRepository) {
        this.repository = repository;
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void markInterruptedTasksFailed() {
        String reason = "服务重启，任务执行中断。请重新发送消息。";
        for (TaskRun run : runRepository.findByStatus(TaskStatus.RUNNING)) {
            for (TaskRunStep step : stepRepository.findByRun_IdAndStatus(run.getId(), TaskRunStepStatus.RUNNING)) {
                step.markFailed(reason);
            }
            run.markFailed(reason);
        }
        for (Task task : repository.findByStatus(TaskStatus.RUNNING)) {
            task.markFailed(reason);
        }
    }
}
