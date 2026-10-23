package com.staranaly.arbiter.share;

import java.time.LocalDateTime;

public record XiaohongshuNoteResponse(Long id, XiaohongshuNote.Status status, String requirements,
        String title, String body, String tagsText, String imageName, int imageSize,
        LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime publishedAt, Long taskId,
        Integer directionIndex) {
    public static XiaohongshuNoteResponse from(XiaohongshuNote note) {
        return new XiaohongshuNoteResponse(note.getId(), note.getStatus(), note.getRequirements(),
                note.getTitle(), note.getBody(), note.getTagsText(), note.getImageName(),
                note.getImageSize(), note.getCreatedAt(), note.getUpdatedAt(), note.getPublishedAt(),
                note.getTaskId(), note.getDirectionIndex());
    }
}
