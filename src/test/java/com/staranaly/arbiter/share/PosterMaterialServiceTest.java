package com.staranaly.arbiter.share;

import java.util.List;

import com.staranaly.arbiter.knowledge.KnowledgeDocumentParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PosterMaterialServiceTest {
    private final PosterMaterialRepository repository = mock(PosterMaterialRepository.class);
    private final KnowledgeDocumentParser parser = mock(KnowledgeDocumentParser.class);
    private final PosterMaterialService service = new PosterMaterialService(repository, parser);

    @Test
    void acceptsTextAndVerifiedLogoBytes() {
        when(repository.save(any(PosterMaterial.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PosterMaterialResponse text = service.create(" 品牌规范 ", PosterMaterial.Type.TEXT, " 不写虚构价格 ", null);
        assertThat(text.name()).isEqualTo("品牌规范");
        assertThat(text.content()).isEqualTo("不写虚构价格");

        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile image = new MockMultipartFile("file", "logo.png", "image/png", png);
        PosterMaterialResponse logo = service.create("标识", PosterMaterial.Type.LOGO, "放左上角", image);
        assertThat(logo.mime()).isEqualTo("image/png");
        assertThat(logo.content()).isEqualTo("放左上角");

        MockMultipartFile fake = new MockMultipartFile("file", "fake.png", "image/png", new byte[] {1, 2, 3});
        assertThatThrownBy(() -> service.create("假图片", PosterMaterial.Type.LOGO, "", fake))
                .isInstanceOf(XiaohongshuInputException.class).hasMessageContaining("仅支持");
    }

    @Test
    void selectedMaterialsKeepOrderAndRejectTooManyImages() {
        PosterMaterial first = new PosterMaterial("规范", PosterMaterial.Type.TEXT, "只用蓝色", null, null, null);
        PosterMaterial second = new PosterMaterial("标识", PosterMaterial.Type.LOGO, "", "logo.png", "image/png", new byte[] {1});
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 2L);
        when(repository.findAllById(List.of(2L, 1L))).thenReturn(List.of(first, second));
        assertThat(service.selected(List.of(2L, 1L))).containsExactly(second, first);
        assertThatThrownBy(() -> service.selected(List.of(1L, 1L)))
                .isInstanceOf(XiaohongshuInputException.class);
        List<PosterMaterial> images = java.util.stream.LongStream.rangeClosed(3, 6).mapToObj(id -> {
            PosterMaterial item = new PosterMaterial("图片" + id, PosterMaterial.Type.REFERENCE_IMAGE,
                    "", "image.png", "image/png", new byte[] {1});
            ReflectionTestUtils.setField(item, "id", id);
            return item;
        }).toList();
        when(repository.findAllById(List.of(3L, 4L, 5L, 6L))).thenReturn(images);
        assertThatThrownBy(() -> service.selected(List.of(3L, 4L, 5L, 6L)))
                .isInstanceOf(XiaohongshuInputException.class).hasMessageContaining("最多选择 3 张");
    }
}
