package com.staranaly.arbiter.task;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaskService service;

    @Test
    void createsTaskAndReturnsItsLocation() throws Exception {
        TaskResponse task = new TaskResponse(42L, "分析销售额", "按月汇总", TaskStatus.PENDING,
                Instant.parse("2026-10-20T02:00:00Z"), null);
        when(service.create(any(CreateTaskRequest.class))).thenReturn(task);

        mvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"分析销售额","description":"按月汇总"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/tasks/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-20T02:00:00Z"));
    }

    @Test
    void rejectsBlankTitle() throws Exception {
        mvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void returnsTaskById() throws Exception {
        TaskResponse task = new TaskResponse(42L, "分析销售额", null, TaskStatus.PENDING,
                Instant.parse("2026-10-20T02:00:00Z"), null);
        when(service.findById(42L)).thenReturn(task);

        mvc.perform(get("/api/tasks/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("分析销售额"));
    }

    @Test
    void returnsNotFoundForMissingTask() throws Exception {
        when(service.findById(99L)).thenThrow(new TaskNotFoundException(99L));

        mvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsTasksWithPagination() throws Exception {
        TaskResponse task = new TaskResponse(42L, "分析销售额", null, TaskStatus.PENDING,
                Instant.parse("2026-10-20T02:00:00Z"), null);
        when(service.findAll(any(), isNull(), isNull()))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(42))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    @Test
    void acceptsStatusAndTitleSearchTogether() throws Exception {
        TaskResponse task = new TaskResponse(42L, "分析销售额", null, TaskStatus.FAILED,
                Instant.parse("2026-10-20T02:00:00Z"), null);
        when(service.findAll(any(), eq(TaskStatus.FAILED), eq("分折销售额")))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/tasks").param("status", "FAILED").param("query", "分折销售额"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(42))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
