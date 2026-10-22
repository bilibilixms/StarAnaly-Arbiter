package com.staranaly.arbiter.share;

import java.util.List;
import java.util.Optional;

import com.staranaly.arbiter.knowledge.KnowledgeDocumentParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XiaohongshuMaterialServiceTest {
    private final XiaohongshuMaterialRepository repository = mock(XiaohongshuMaterialRepository.class);
    private final KnowledgeDocumentParser parser = mock(KnowledgeDocumentParser.class);
    private final XiaohongshuMaterialService service = new XiaohongshuMaterialService(repository, parser);

    @Test
    void savesTypedTextAndReplacesItWithExtractedWordText() {
        when(repository.save(any(XiaohongshuMaterial.class))).thenAnswer(invocation -> invocation.getArgument(0));
        XiaohongshuMaterialResponse created = service.create(" 我的风格 ", " 第一版文字 ", null);
        assertThat(created.name()).isEqualTo("我的风格");
        assertThat(created.content()).isEqualTo("第一版文字");

        XiaohongshuMaterial stored = new XiaohongshuMaterial(created.name(), created.content(), null);
        ReflectionTestUtils.setField(stored, "id", 7L);
        when(repository.findById(7L)).thenReturn(Optional.of(stored));
        byte[] bytes = "file".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "style.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);
        when(parser.parse("style.docx", bytes)).thenReturn("文档里的真实细节");

        XiaohongshuMaterialResponse updated = service.update(7L, "探店资料", null, file);

        assertThat(updated.content()).isEqualTo("文档里的真实细节");
        assertThat(updated.sourceName()).isEqualTo("style.docx");
        verify(parser).parse("style.docx", bytes);
    }

    @Test
    void selectedMaterialsRequireExistingDistinctIds() {
        XiaohongshuMaterial material = new XiaohongshuMaterial("风格", "短句", null);
        ReflectionTestUtils.setField(material, "id", 3L);
        when(repository.findAllById(List.of(3L))).thenReturn(List.of(material));

        assertThat(service.selected(List.of(3L))).containsExactly(material);
        assertThatThrownBy(() -> service.selected(List.of(3L, 3L)))
                .isInstanceOf(XiaohongshuInputException.class);
        assertThatThrownBy(() -> service.selected(List.of(99L)))
                .isInstanceOf(XiaohongshuInputException.class)
                .hasMessageContaining("不存在");
    }
}
