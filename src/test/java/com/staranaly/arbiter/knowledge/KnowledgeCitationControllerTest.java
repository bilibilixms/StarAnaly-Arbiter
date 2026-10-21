package com.staranaly.arbiter.knowledge;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KnowledgeCitationController.class)
class KnowledgeCitationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private KnowledgeService service;

    @Test
    void listsCitationsWithDocumentAndLines() throws Exception {
        when(service.citations(42L, 7L)).thenReturn(List.of(new KnowledgeCitation(
                11L, 3L, "规则.md", 3, 4, "14 天内可以申请退货。")));

        mvc.perform(get("/api/tasks/42/runs/7/citations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentName").value("规则.md"))
                .andExpect(jsonPath("$[0].startLine").value(3))
                .andExpect(jsonPath("$[0].endLine").value(4));
    }
}
