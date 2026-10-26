package com.staranaly.arbiter.share;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import com.staranaly.arbiter.task.TaskRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PosterGenerationServiceTest {

    @Test
    void rejectsMoreThanThreeInputImagesBeforeCallingModel() {
        PosterGenerationService service = new PosterGenerationService(mock(TaskRepository.class),
                mock(JdbcTemplate.class), new ObjectMapper(), "test-key", "https://example.com/api/v1", "qwen-image-3.0-pro");
        PosterMaterial image = new PosterMaterial("参考图", PosterMaterial.Type.REFERENCE_IMAGE, "", "image.png", "image/png", new byte[] {1});
        MockMultipartFile reference = new MockMultipartFile("referenceImage", "reference.png", "image/png", new byte[] {1});

        assertThatThrownBy(() -> service.generate(1, "海报主题", "", 1, reference,
                List.of(image, image, image)))
                .isInstanceOf(XiaohongshuInputException.class).hasMessageContaining("最多接收 3 张");
    }

    @Test
    void explainsProviderFailuresByActionableCategory() {
        assertThat(PosterGenerationService.providerError(401, "")).contains("密钥");
        assertThat(PosterGenerationService.providerError(429, "")).contains("额度");
        assertThat(PosterGenerationService.providerError(504, "")).contains("超时");
        assertThat(PosterGenerationService.providerError(400, "")).contains("参考图");
    }

    @Test
    void readsEveryGeneratedImageAcrossChoices() throws Exception {
        var response = new ObjectMapper().readTree("""
                {
                  "output": {
                    "choices": [
                      {"message": {"content": [{"image": "https://example.com/first.png"}]}},
                      {"message": {"content": [{"image": "https://example.com/second.png"}]}}
                    ]
                  }
                }
                """);

        assertThat(PosterGenerationService.imageUrls(response))
                .containsExactly("https://example.com/first.png", "https://example.com/second.png");
    }
}
