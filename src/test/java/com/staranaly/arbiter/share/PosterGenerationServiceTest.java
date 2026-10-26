package com.staranaly.arbiter.share;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PosterGenerationServiceTest {

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
