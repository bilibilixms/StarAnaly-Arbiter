package com.staranaly.arbiter.csv;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CsvFileController.class)
class CsvFileControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CsvFileService service;

    @Test
    void uploadsAndPreviewsCsv() throws Exception {
        CsvFileMetadata file = new CsvFileMetadata(3L, 42L, "sales.csv", 30, 1,
                List.of("date", "amount"), Instant.parse("2026-10-21T00:00:00Z"));
        when(service.upload(org.mockito.ArgumentMatchers.eq(42L), any())).thenReturn(file);
        when(service.preview(42L, 3L, 20)).thenReturn(new CsvPreviewResponse(file, List.of()));

        mvc.perform(multipart("/api/tasks/42/files")
                        .file(new MockMultipartFile("file", "sales.csv", "text/csv",
                                "date,amount\n2026-01-01,100\n".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/tasks/42/files/3"))
                .andExpect(jsonPath("$.rowCount").value(1));
        mvc.perform(get("/api/tasks/42/files/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file.id").value(3));
    }
}
