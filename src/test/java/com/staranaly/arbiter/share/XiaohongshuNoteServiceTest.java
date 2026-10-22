package com.staranaly.arbiter.share;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class XiaohongshuNoteServiceTest {
    private final XiaohongshuNoteRepository notes = mock(XiaohongshuNoteRepository.class);
    private final XiaohongshuNoteImageRepository images = mock(XiaohongshuNoteImageRepository.class);
    private final XiaohongshuNoteService service = new XiaohongshuNoteService(notes, images);

    @Test
    void rejectsNonImageBeforeSaving() {
        MockMultipartFile fake = new MockMultipartFile("image", "fake.jpg", "image/jpeg", "not an image".getBytes());

        assertThatThrownBy(() -> service.create(fake, "写一篇笔记", "标题", "正文", "#日常"))
                .isInstanceOf(XiaohongshuInputException.class)
                .hasMessageContaining("JPEG");
        verifyNoInteractions(notes, images);
    }

    @Test
    void editingDraftUpdatesTextAndOriginalImage() throws IOException {
        XiaohongshuNote note = new XiaohongshuNote("旧要求", "旧标题", "旧正文", "", "old.jpg", "image/jpeg", 4);
        XiaohongshuNoteImage storedImage = new XiaohongshuNoteImage(1L, new byte[] {1});
        when(notes.findById(1L)).thenReturn(Optional.of(note));
        when(images.findById(1L)).thenReturn(Optional.of(storedImage));
        MockMultipartFile replacement = new MockMultipartFile("image", "new.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});

        service.update(1L, replacement, "", "新标题", "新正文", "#旅行");

        assertThat(note.getTitle()).isEqualTo("新标题");
        assertThat(note.getRequirements()).isEmpty();
        assertThat(note.getTagsText()).isEqualTo("#旅行");
        assertThat(note.getImageName()).isEqualTo("new.png");
        assertThat(storedImage.getImageData()).isEqualTo(replacement.getBytes());
        verify(notes).flush();
    }

    @Test
    void publishedNoteCannotBeEditedOrDeleted() {
        XiaohongshuNote note = new XiaohongshuNote("要求", "标题", "正文", "", "photo.jpg", "image/jpeg", 4);
        when(notes.findById(1L)).thenReturn(Optional.of(note));

        service.markPublished(1L);

        assertThat(note.getStatus()).isEqualTo(XiaohongshuNote.Status.PUBLISHED);
        assertThat(note.getPublishedAt()).isNotNull();
        assertThat(service.markPublished(1L).publishedAt()).isEqualTo(note.getPublishedAt());
        assertThatThrownBy(() -> service.update(1L, null, "要求", "标题", "正文", ""))
                .isInstanceOf(XiaohongshuInputException.class);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(XiaohongshuInputException.class);
        verifyNoInteractions(images);
    }
}
