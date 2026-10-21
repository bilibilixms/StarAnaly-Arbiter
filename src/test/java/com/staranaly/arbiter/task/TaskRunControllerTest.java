package com.staranaly.arbiter.task;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskRunController.class)
class TaskRunControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaskConversationService service;

    @Test
    void startsRunAndReturnsLocation() throws Exception {
        TaskRunResponse run = new TaskRunResponse(7L, 42L, TaskStatus.RUNNING, "用户消息，4 字符", null,
                Instant.parse("2026-10-21T02:00:00Z"), null);
        when(service.start(42L, "继续分析")).thenReturn(run);

        mvc.perform(post("/api/tasks/42/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"继续分析"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/tasks/42/runs/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void returnsRunAndSteps() throws Exception {
        Instant started = Instant.parse("2026-10-21T02:00:00Z");
        when(service.findRun(42L, 7L)).thenReturn(new TaskRunResponse(
                7L, 42L, TaskStatus.SUCCEEDED, "用户消息，4 字符", null, started, started.plusSeconds(2)));
        when(service.listSteps(42L, 7L)).thenReturn(List.of(new TaskRunStepResponse(
                11L, 1, TaskRunStepKind.MODEL_CALL, TaskRunStepStatus.SUCCEEDED,
                null, "请求模型生成回复", "模型返回 6 字符", started, started.plusSeconds(2))));

        mvc.perform(get("/api/tasks/42/runs/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(get("/api/tasks/42/runs/7/steps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stepNumber").value(1))
                .andExpect(jsonPath("$[0].kind").value("MODEL_CALL"));
    }

    @Test
    void rejectsBlankInput() throws Exception {
        mvc.perform(post("/api/tasks/42/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"  "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
