package com.staranaly.arbiter.knowledge;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KnowledgeController.class)
class KnowledgeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private KnowledgeService service;

    @Test
    void uploadsAndListsDocuments() throws Exception {
        KnowledgeDocumentMetadata document = new KnowledgeDocumentMetadata(
                3L, 42L, "规则.md", 50, 2, Instant.parse("2026-10-21T00:00:00Z"));
        when(service.upload(eq(42L), any())).thenReturn(document);
        when(service.list(42L)).thenReturn(List.of(document));

        mvc.perform(multipart("/api/tasks/42/documents")
                        .file(new MockMultipartFile("file", "规则.md", "text/markdown",
                                "退货规则".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/tasks/42/documents/3"))
                .andExpect(jsonPath("$.chunkCount").value(2));
        mvc.perform(get("/api/tasks/42/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("规则.md"));
    }

    @Test
    void searchReturnsSourceLocationAndInvalidQueryReturnsProblem() throws Exception {
        when(service.search(42L, "退货期限", 2)).thenReturn(new KnowledgeSearchResponse(
                "退货期限", List.of(new KnowledgeHit(11L, 3L, "规则.md", 3, 3,
                        "签收日起 14 天内", 2))));
        when(service.search(42L, "?", 5)).thenThrow(new KnowledgeInputException("检索词至少需要两个有效字符。"));

        mvc.perform(get("/api/tasks/42/documents/search")
                        .param("query", "退货期限").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0].startLine").value(3))
                .andExpect(jsonPath("$.hits[0].excerpt").value("签收日起 14 天内"));
        mvc.perform(get("/api/tasks/42/documents/search").param("query", "?"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("检索词至少需要两个有效字符。"));
    }

    @Test
    void missingDocumentReturnsProblem() throws Exception {
        doThrow(new KnowledgeDocumentNotFoundException(42L, 3L)).when(service).delete(42L, 3L);

        mvc.perform(delete("/api/tasks/42/documents/3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Knowledge document 3 was not found for task 42"));
    }
}
