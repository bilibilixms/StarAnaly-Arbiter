package com.staranaly.arbiter.share;

import java.time.LocalDateTime;

public record DouyinNoteResponse(Long id, DouyinNote.Status status, String requirements,
        String title, String body, String tagsText, String imageName, int imageSize,
        LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime publishedAt, Long taskId,
        Integer directionIndex) {
    public static DouyinNoteResponse from(DouyinNote note) {
        return new DouyinNoteResponse(note.getId(), note.getStatus(), note.getRequirements(),
                note.getTitle(), note.getBody(), note.getTagsText(), note.getImageName(),
                note.getImageSize(), note.getCreatedAt(), note.getUpdatedAt(), note.getPublishedAt(),
                note.getTaskId(), note.getDirectionIndex());
    }
}
