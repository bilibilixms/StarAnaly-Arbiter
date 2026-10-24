package com.staranaly.arbiter.share;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.model.ModelConversationMessage;
import com.staranaly.arbiter.model.ModelGateway;
import com.staranaly.arbiter.model.ModelReply;
import com.staranaly.arbiter.task.CreativeBriefService;
import com.staranaly.arbiter.task.CreativeBrief;
import com.staranaly.arbiter.task.CreativeBriefResponse;
import com.staranaly.arbiter.task.TaskImageService;
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
    private final CreativeBriefService briefs = mock(CreativeBriefService.class);
    private final TaskImageService taskImages = mock(TaskImageService.class);
    private final XiaohongshuDraftService service = new XiaohongshuDraftService(gateway, new ObjectMapper(),
            briefs, taskImages);

    private CreativeBriefResponse linkedBrief(boolean confirmed) {
        return new CreativeBriefResponse("介绍活动", "周末游客", "有免费试玩", "平实",
                List.of(new CreativeBrief.Fact("周六开放试玩", "文档：活动说明.pdf")),
                List.of("是否需要预约"), List.of(new CreativeBrief.Direction("现场体验", "介绍试玩流程", "周六去看看")),
                confirmed, false, Instant.now());
    }

    @Test
    void linkedGenerationIncludesConfirmedFactsAndKeepsUnknownsOutOfClaims() {
        MultipartFile image = new MockMultipartFile("image", "photo.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        when(briefs.get(42L)).thenReturn(Optional.of(linkedBrief(true)));
        when(taskImages.list(42L)).thenReturn(List.of());
        when(gateway.next(anyList(), eq(false), any(Duration.class)))
                .thenReturn(new ModelReply("{\"title\":\"周末试玩\",\"body\":\"周六开放试玩。\",\"hashtags\":[]}", List.of()));

        assertThat(service.generate(image, "写得简短", List.of(), 42L, 0).title()).isEqualTo("周末试玩");
        verify(gateway).next(org.mockito.ArgumentMatchers.argThat(messages -> {
            Object content = messages.get(1).toRequest().get("content");
            if (!(content instanceof List<?> parts) || !(parts.get(0) instanceof Map<?, ?> text)) return false;
            String prompt = text.get("text").toString();
            return prompt.contains("现场体验") && prompt.contains("周六开放试玩")
                    && prompt.contains("文档：活动说明.pdf") && prompt.contains("是否需要预约")
                    && prompt.contains("不得当作事实") && prompt.contains("写得简短");
        }), eq(false), any(Duration.class));
    }

    @Test
    void linkedGenerationCanProceedWithoutConfirmedBrief() {
        MultipartFile image = new MockMultipartFile("image", "photo.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        when(briefs.get(42L)).thenReturn(Optional.of(linkedBrief(false)));
        when(taskImages.list(42L)).thenReturn(List.of());
        when(gateway.next(anyList(), eq(false), any(Duration.class)))
                .thenReturn(new ModelReply("{\"title\":\"宣传海报\",\"body\":\"看看这张海报。\",\"hashtags\":[]}", List.of()));

        assertThat(service.generate(image, "海报来自需求单", List.of(), 42L, null).title()).isEqualTo("宣传海报");
        verify(gateway).next(org.mockito.ArgumentMatchers.argThat(messages -> {
            Object content = messages.get(1).toRequest().get("content");
            if (!(content instanceof List<?> parts) || !(parts.get(0) instanceof Map<?, ?> text)) return false;
            String prompt = text.get("text").toString();
            return prompt.contains("海报来自需求单") && !prompt.contains("周六开放试玩");
        }), eq(false), any(Duration.class));
    }

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
    void recognizesAllImagesInMultiImageDraft() {
        MultipartFile first = new MockMultipartFile("image", "one.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        MultipartFile second = new MockMultipartFile("extraImages", "two.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        when(gateway.next(anyList(), eq(false), any(Duration.class)))
                .thenReturn(new ModelReply("{\"title\":\"两张图\",\"body\":\"记录这两张图片。\",\"hashtags\":[]}", List.of()));

        assertThat(service.generate(first, List.of(second), "", List.of(), null, null).title()).isEqualTo("两张图");
        verify(gateway).next(org.mockito.ArgumentMatchers.argThat(messages ->
                messages.size() == 3 && messages.get(2).toRequest().get("content").toString().contains("image/png")),
                eq(false), any(Duration.class));
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
