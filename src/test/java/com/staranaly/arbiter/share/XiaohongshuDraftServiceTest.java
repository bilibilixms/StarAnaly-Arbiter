package com.staranaly.arbiter.share;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.model.ModelConversationMessage;
import com.staranaly.arbiter.model.ModelGateway;
import com.staranaly.arbiter.model.ModelReply;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class XiaohongshuDraftServiceTest {

    private final ModelGateway gateway = mock(ModelGateway.class);
    private final XiaohongshuDraftService service = new XiaohongshuDraftService(gateway, new ObjectMapper());

    @Test
    void sendsImageAndRequirementsToModelAndReturnsDraft() {
        byte[] imageBytes = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
        MultipartFile image = new MockMultipartFile("image", "photo.jpg", "image/jpeg",
                imageBytes);
        when(gateway.next(anyList(), eq(false), any(Duration.class)))
                .thenReturn(new ModelReply("""
                        {"title":"周末的小发现","body":"阳光正好，记录一下眼前的风景。","hashtags":["周末日常","生活记录"]}
                        """, List.of()));

        XiaohongshuDraftResponse result = service.generate(image, "写一篇轻松的周末笔记");

        assertThat(result.title()).isEqualTo("周末的小发现");
        assertThat(result.hashtags()).containsExactly("周末日常", "生活记录");
        verify(gateway).next(org.mockito.ArgumentMatchers.argThat(messages -> {
            if (messages.size() != 2 || !"system".equals(messages.get(0).role())) return false;
            ModelConversationMessage user = messages.get(1);
            Object content = user.toRequest().get("content");
            if (!(content instanceof List<?> parts) || parts.size() != 2) return false;
            if (!(parts.get(0) instanceof Map<?, ?> text)
                    || !(parts.get(1) instanceof Map<?, ?> picture)) return false;
            Object imageUrl = picture.get("image_url");
            return "user".equals(user.role())
                    && text.get("text").toString().contains("写一篇轻松的周末笔记")
                    && imageUrl instanceof Map<?, ?> url
                    && ("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes))
                            .equals(url.get("url"));
        }), eq(false), any(Duration.class));
    }

    @Test
    void generatesFromImageWithoutTextRequirements() {
        MultipartFile image = new MockMultipartFile("image", "photo.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        when(gateway.next(anyList(), eq(false), any(Duration.class)))
                .thenReturn(new ModelReply("""
                        {"title":"图片日记","body":"记录眼前的画面。","hashtags":["生活记录"]}
                        """, List.of()));

        assertThat(service.generate(image, "   ").title()).isEqualTo("图片日记");
        verify(gateway).next(org.mockito.ArgumentMatchers.argThat(messages -> {
            Object content = messages.get(1).toRequest().get("content");
            if (!(content instanceof List<?> parts) || !(parts.get(0) instanceof Map<?, ?> text)) return false;
            return text.get("text").toString().contains("根据这张图片创作");
        }), eq(false), any(Duration.class));
    }

    @Test
    void putsSavedMaterialBeforeCurrentRequirementsInImagePrompt() {
        MultipartFile image = new MockMultipartFile("image", "photo.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        XiaohongshuMaterial material = new XiaohongshuMaterial("我的语气", "我习惯写短句，记录真实细节。", null);
        when(gateway.next(anyList(), eq(false), any(Duration.class)))
                .thenReturn(new ModelReply("{" + "\"title\":\"窗边\",\"body\":\"今天坐了窗边。\",\"hashtags\":[]}", List.of()));

        service.generate(image, "重点写窗边", List.of(material));

        verify(gateway).next(org.mockito.ArgumentMatchers.argThat(messages -> {
            Object content = messages.get(1).toRequest().get("content");
            if (!(content instanceof List<?> parts) || !(parts.get(0) instanceof Map<?, ?> text)) return false;
            String prompt = text.get("text").toString();
            return prompt.contains("我的语气") && prompt.contains("我习惯写短句")
                    && prompt.indexOf("我习惯写短句") < prompt.indexOf("重点写窗边")
                    && messages.get(0).toRequest().get("content").toString().contains("少用空泛赞美");
        }), eq(false), any(Duration.class));
    }

    @Test
    void rejectsOversizedAndNonImageFilesBeforeCallingModel() {
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(20_000_001L);
        assertThatThrownBy(() -> service.generate(oversized, "写笔记"))
                .isInstanceOf(XiaohongshuInputException.class)
                .hasMessageContaining("20 MB");

        MultipartFile fakeImage = new MockMultipartFile("image", "fake.jpg", "image/jpeg",
                "not a picture".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.generate(fakeImage, "写笔记"))
                .isInstanceOf(XiaohongshuInputException.class)
                .hasMessageContaining("JPEG");
        verifyNoInteractions(gateway);
    }
}
