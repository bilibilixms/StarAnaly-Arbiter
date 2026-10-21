package com.staranaly.arbiter.task;

import java.util.List;
import java.util.Optional;

import com.staranaly.arbiter.model.ModelMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskConversationStateTest {

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final TaskMessageRepository messageRepository = mock(TaskMessageRepository.class);
    private final TaskRunRepository runRepository = mock(TaskRunRepository.class);
    private final TaskRunStepRepository stepRepository = mock(TaskRunStepRepository.class);
    private final TaskConversationState state = new TaskConversationState(
            taskRepository, messageRepository, runRepository, stepRepository);

    @Test
    void includesTaskContextAndRecentMessagesInChronologicalOrder() {
        Task task = new Task("分析销售额", "按月汇总");
        when(taskRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(task));
        when(messageRepository.findTop20ByTaskIdOrderByIdDesc(42L)).thenReturn(List.of(
                new TaskMessage(task, "ASSISTANT", "上次回答"),
                new TaskMessage(task, "USER", "上次问题")
        ));
        when(runRepository.save(any(TaskRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ModelMessage> prompt = state.begin(42L, "继续分析").context();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(prompt).extracting(ModelMessage::role)
                .containsExactly("system", "user", "user", "assistant", "user");
        assertThat(prompt.get(1).content()).contains("分析销售额", "按月汇总");
        assertThat(prompt.get(4).content()).isEqualTo("继续分析");
    }

    @Test
    void preventsAnotherMessageWhileTaskIsRunning() {
        Task task = new Task("分析销售额", null);
        task.markRunning();
        when(taskRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> state.begin(42L, "继续"))
                .isInstanceOf(TaskBusyException.class);
    }

    @Test
    void marksRunStepAndTaskFailedTogether() {
        Task task = new Task("分析销售额", null);
        ReflectionTestUtils.setField(task, "id", 42L);
        task.markRunning();
        TaskRun run = new TaskRun(task, "继续分析");
        ReflectionTestUtils.setField(run, "id", 7L);
        TaskRunStep step = new TaskRunStep(run, 1, TaskRunStepKind.MODEL_CALL, "请求模型生成回复");
        when(runRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(run));
        when(taskRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(task));
        when(stepRepository.findByRun_IdAndStatus(7L, TaskRunStepStatus.RUNNING)).thenReturn(List.of(step));

        state.fail(7L, "模型服务不可用");

        assertThat(run.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(run.getLastError()).isEqualTo("模型服务不可用");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(step.getStatus()).isEqualTo(TaskRunStepStatus.FAILED);
        assertThat(run.getFinishedAt()).isNotNull();
    }

    @Test
    void savesReplyAndMarksRunStepAndTaskSucceeded() {
        Task task = new Task("分析销售额", null);
        ReflectionTestUtils.setField(task, "id", 42L);
        task.markRunning();
        TaskRun run = new TaskRun(task, "继续分析");
        ReflectionTestUtils.setField(run, "id", 7L);
        TaskRunStep step = new TaskRunStep(run, 1, TaskRunStepKind.MODEL_CALL, "请求模型生成回复");
        when(runRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(run));
        when(taskRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(task));
        when(stepRepository.findByRun_IdAndStepNumber(7L, 1)).thenReturn(Optional.of(step));
        when(messageRepository.save(any(TaskMessage.class))).thenAnswer(invocation -> {
            TaskMessage message = invocation.getArgument(0);
            message.setCreationTime();
            return message;
        });

        state.completeStep(7L, 1, "模型返回 5 字符");
        TaskMessageResponse reply = state.succeed(7L, "继续分析", "已完成分析", 2);

        assertThat(reply.content()).isEqualTo("已完成分析");
        assertThat(run.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(step.getStatus()).isEqualTo(TaskRunStepStatus.SUCCEEDED);
        verify(stepRepository).save(argThat(saved -> saved.getKind() == TaskRunStepKind.FINAL_ANSWER
                && saved.getStatus() == TaskRunStepStatus.SUCCEEDED));
    }
}
