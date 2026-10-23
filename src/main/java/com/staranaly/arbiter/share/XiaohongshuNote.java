package com.staranaly.arbiter.share;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "xiaohongshu_notes")
public class XiaohongshuNote {
    public enum Status { DRAFT, PUBLISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String requirements;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "tags_text", nullable = false, length = 300)
    private String tagsText;

    @Column(name = "image_name", nullable = false)
    private String imageName;

    @Column(name = "image_mime", nullable = false, length = 30)
    private String imageMime;

    @Column(name = "image_size", nullable = false)
    private int imageSize;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "direction_index")
    private Integer directionIndex;

    protected XiaohongshuNote() {}

    public XiaohongshuNote(String requirements, String title, String body, String tagsText,
            String imageName, String imageMime, int imageSize) {
        this(requirements, title, body, tagsText, imageName, imageMime, imageSize, null);
    }

    public XiaohongshuNote(String requirements, String title, String body, String tagsText,
            String imageName, String imageMime, int imageSize, Long taskId) {
        this(requirements, title, body, tagsText, imageName, imageMime, imageSize, taskId, null);
    }

    public XiaohongshuNote(String requirements, String title, String body, String tagsText,
            String imageName, String imageMime, int imageSize, Long taskId, Integer directionIndex) {
        updateText(requirements, title, body, tagsText);
        updateImage(imageName, imageMime, imageSize);
        this.taskId = taskId;
        this.directionIndex = directionIndex;
    }

    @PrePersist
    void created() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void updateText(String requirements, String title, String body, String tagsText) {
        this.requirements = requirements;
        this.title = title;
        this.body = body;
        this.tagsText = tagsText;
    }

    public void updateImage(String imageName, String imageMime, int imageSize) {
        this.imageName = imageName;
        this.imageMime = imageMime;
        this.imageSize = imageSize;
    }

    public void markPublished() {
        status = Status.PUBLISHED;
        publishedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getDirectionIndex() { return directionIndex; }
    public void setDirectionIndex(Integer directionIndex) { this.directionIndex = directionIndex; }
    public Status getStatus() { return status; }
    public String getRequirements() { return requirements; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getTagsText() { return tagsText; }
    public String getImageName() { return imageName; }
    public String getImageMime() { return imageMime; }
    public int getImageSize() { return imageSize; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
}
