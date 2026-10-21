package com.staranaly.arbiter.task;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskStartupRecoveryTest {

    @Test
    void marksInterruptedRunActiveStepAndLegacyTaskFailed() {
        Task task = new Task("中断的任务", null);
        task.markRunning();
        TaskRun run = new TaskRun(task, "继续");
        ReflectionTestUtils.setField(run, "id", 7L);
        TaskRunStep active = new TaskRunStep(run, 2, TaskRunStepKind.TOOL_CALL,
                "sum_two_numbers", "请求工具");
        TaskRunStep completed = new TaskRunStep(run, 1, TaskRunStepKind.MODEL_CALL, "请求模型");
        completed.markSucceeded("模型已返回");
        TaskRepository tasks = mock(TaskRepository.class);
        TaskRunRepository runs = mock(TaskRunRepository.class);
        TaskRunStepRepository steps = mock(TaskRunStepRepository.class);
        when(runs.findByStatus(TaskStatus.RUNNING)).thenReturn(List.of(run));
        when(steps.findByRun_IdAndStatus(7L, TaskRunStepStatus.RUNNING)).thenReturn(List.of(active));
        when(tasks.findByStatus(TaskStatus.RUNNING)).thenReturn(List.of(task));

        new TaskStartupRecovery(tasks, runs, steps).markInterruptedTasksFailed();

        assertThat(run.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(active.getStatus()).isEqualTo(TaskRunStepStatus.FAILED);
        assertThat(completed.getStatus()).isEqualTo(TaskRunStepStatus.SUCCEEDED);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
    }
}
