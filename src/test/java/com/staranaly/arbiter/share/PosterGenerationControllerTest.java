package com.staranaly.arbiter.share;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PosterGenerationController.class)
class PosterGenerationControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private PosterGenerationService generator;
    @MockitoBean private PosterMaterialService materials;
    @MockitoBean private PosterJobService jobs;

    @Test
    void restoresSavedJobAndItsReviewState() throws Exception {
        PosterJobService.Image image = new PosterJobService.Image(23, 0, "poster.png", "image/png", true,
                "{\"requiredText\":true,\"forbiddenContent\":true,\"logo\":true}");
        PosterJobService.Job job = new PosterJobService.Job(17, "SUCCEEDED", "活动海报", "", "[]",
                "{\"requiredText\":\"日期\",\"forbiddenContent\":\"虚假价格\",\"logoName\":\"品牌\",\"logoIds\":[8]}",
                null, List.of(0), List.of(image), 1, LocalDateTime.of(2026, 10, 26, 12, 0));
        when(jobs.get(3, 17)).thenReturn(job);

        mvc.perform(get("/api/posters/tasks/3/jobs/17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.selected[0]").value(0))
                .andExpect(jsonPath("$.images[0].logoApplied").value(true))
                .andExpect(jsonPath("$.images[0].reviewJson").value(image.reviewJson()));
    }

    @Test
    void savesSelectionAndPerImageReviewThenAllowsCancellation() throws Exception {
        mvc.perform(put("/api/posters/tasks/3/jobs/17/selection")
                        .contentType(MediaType.APPLICATION_JSON).content("[0,2]"))
                .andExpect(status().isNoContent());
        verify(jobs).select(3, 17, List.of(0, 2));

        mvc.perform(put("/api/posters/tasks/3/jobs/17/images/23/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requiredText\":true,\"forbiddenContent\":true,\"logo\":false}"))
                .andExpect(status().isNoContent());
        verify(jobs).review(3, 17, 23, new PosterJobService.Review(true, true, false));

        mvc.perform(post("/api/posters/tasks/3/jobs/17/cancel"))
                .andExpect(status().isNoContent());
        verify(jobs).cancel(3, 17);
    }
}
