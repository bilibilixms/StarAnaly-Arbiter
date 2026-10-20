package com.staranaly.arbiter.task;

import java.util.List;
import java.util.Optional;

import com.staranaly.arbiter.model.ModelMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskConversationStateTest {

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final TaskMessageRepository messageRepository = mock(TaskMessageRepository.class);
    private final TaskConversationState state = new TaskConversationState(taskRepository, messageRepository);

    @Test
    void includesTaskContextAndRecentMessagesInChronologicalOrder() {
        Task task = new Task("分析销售额", "按月汇总");
        when(taskRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(task));
        when(messageRepository.findTop20ByTaskIdOrderByIdDesc(42L)).thenReturn(List.of(
                new TaskMessage(task, "ASSISTANT", "上次回答"),
                new TaskMessage(task, "USER", "上次问题")
        ));

        List<ModelMessage> prompt = state.begin(42L, "继续分析");

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
}
