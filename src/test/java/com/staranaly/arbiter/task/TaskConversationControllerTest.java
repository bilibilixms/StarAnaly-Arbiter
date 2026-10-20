package com.staranaly.arbiter.task;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskConversationController.class)
class TaskConversationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaskConversationService service;

    @Test
    void sendsMessageAndReturnsAssistantReply() throws Exception {
        TaskMessageResponse reply = new TaskMessageResponse(2L, "ASSISTANT", "已完成分析",
                Instant.parse("2026-10-20T02:00:00Z"));
        when(service.send(eq(42L), eq("请分析销售额"))).thenReturn(reply);

        mvc.perform(post("/api/tasks/42/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"请分析销售额"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.content").value("已完成分析"));
    }

    @Test
    void listsConversationInStoredOrder() throws Exception {
        when(service.list(42L)).thenReturn(List.of(
                new TaskMessageResponse(1L, "USER", "你好", Instant.parse("2026-10-20T02:00:00Z")),
                new TaskMessageResponse(2L, "ASSISTANT", "你好", Instant.parse("2026-10-20T02:00:01Z"))
        ));

        mvc.perform(get("/api/tasks/42/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"));
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mvc.perform(post("/api/tasks/42/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"   "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsConcurrentExecution() throws Exception {
        when(service.send(eq(42L), eq("继续"))).thenThrow(new TaskBusyException(42L));

        mvc.perform(post("/api/tasks/42/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"继续"}
                                """))
                .andExpect(status().isConflict());
    }
}
