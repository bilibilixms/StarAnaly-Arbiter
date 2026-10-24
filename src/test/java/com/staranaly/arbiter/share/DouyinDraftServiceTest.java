package com.staranaly.arbiter.share;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.model.ModelGateway;
import com.staranaly.arbiter.model.ModelReply;
import com.staranaly.arbiter.task.CreativeBriefService;
import com.staranaly.arbiter.task.TaskImageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinDraftServiceTest {

    @Test
    void generatesDouyinCopyFromImageWithoutXiaohongshuInstructions() {
        ModelGateway gateway = mock(ModelGateway.class);
        DouyinDraftService service = new DouyinDraftService(gateway, new ObjectMapper(),
                mock(CreativeBriefService.class), mock(TaskImageService.class));
        var image = new MockMultipartFile("image", "photo.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        when(gateway.next(any(), eq(false), any(Duration.class)))
                .thenReturn(new ModelReply("{\"title\":\"周末随拍\",\"body\":\"记录这一刻。\",\"hashtags\":[\"日常\"]}", List.of()));

        DouyinDraftResponse draft = service.generate(image, "语气自然");

        assertThat(draft.title()).isEqualTo("周末随拍");
        assertThat(draft.hashtags()).containsExactly("日常");
        verify(gateway).next(argThat(messages -> {
            String system = messages.get(0).toRequest().get("content").toString();
            String user = messages.get(1).toRequest().get("content").toString();
            return system.contains("抖音图文") && !system.contains("小红书")
                    && user.contains("语气自然") && user.contains("data:image/jpeg;base64");
        }), eq(false), any(Duration.class));
    }
}
