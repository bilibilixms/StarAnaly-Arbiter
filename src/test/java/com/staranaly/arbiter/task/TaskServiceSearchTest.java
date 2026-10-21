package com.staranaly.arbiter.task;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceSearchTest {

    @Test
    void typoSearchKeepsSameNameTasksAndPaginatesAcrossAllMatches() {
        TaskRepository repository = mock(TaskRepository.class);
        TaskService service = new TaskService(repository);
        when(repository.findSearchCandidatesByStatus(TaskStatus.PENDING)).thenReturn(List.of(
                candidate(1L, "分析销售额"), candidate(2L, "分析销售额"),
                candidate(3L, "采购计划")));
        Task first = task(1L, "分析销售额");
        Task second = task(2L, "分析销售额");
        when(repository.findAllById(any())).thenReturn(List.of(first, second));

        Page<TaskResponse> firstPage = service.findAll(PageRequest.of(0, 1), TaskStatus.PENDING, "分折销售额");
        Page<TaskResponse> secondPage = service.findAll(PageRequest.of(1, 1), TaskStatus.PENDING, "分折销售额");

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).extracting(TaskResponse::id).containsExactly(2L);
        assertThat(secondPage.getContent()).extracting(TaskResponse::id).containsExactly(1L);
        verify(repository, times(2)).findSearchCandidatesByStatus(TaskStatus.PENDING);
    }

    private static TaskSearchCandidate candidate(Long id, String title) {
        return new TaskSearchCandidate() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getTitle() {
                return title;
            }
        };
    }

    private static Task task(Long id, String title) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(id);
        when(task.getTitle()).thenReturn(title);
        when(task.getStatus()).thenReturn(TaskStatus.PENDING);
        when(task.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 20, 2, 0));
        return task;
    }
}
