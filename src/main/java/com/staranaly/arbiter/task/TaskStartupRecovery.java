package com.staranaly.arbiter.task;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskStartupRecovery {

    private final TaskRepository repository;

    public TaskStartupRecovery(TaskRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void markInterruptedTasksFailed() {
        for (Task task : repository.findByStatus(TaskStatus.RUNNING)) {
            task.markFailed("服务重启，任务执行中断。请重新发送消息。");
        }
    }
}
