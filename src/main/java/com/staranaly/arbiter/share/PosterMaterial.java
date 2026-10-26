package com.staranaly.arbiter.share;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "poster_materials")
public class PosterMaterial {
    public enum Type { TEXT, LOGO, REFERENCE_IMAGE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 60)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;
    @Column(columnDefinition = "TEXT")
    private String content;
    @Column(name = "source_name", length = 255)
    private String sourceName;
    @Column(length = 30)
    private String mime;
    @Lob
    @Column(name = "image_data", columnDefinition = "LONGBLOB")
    private byte[] imageData;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PosterMaterial() {}

    public PosterMaterial(String name, Type type, String content, String sourceName, String mime, byte[] imageData) {
        createdAt = LocalDateTime.now();
        replace(name, type, content, sourceName, mime, imageData);
    }

    public void replace(String name, Type type, String content, String sourceName, String mime, byte[] imageData) {
        this.name = name;
        this.type = type;
        this.content = content;
        this.sourceName = sourceName;
        this.mime = mime;
        this.imageData = imageData;
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public String getContent() { return content; }
    public String getSourceName() { return sourceName; }
    public String getMime() { return mime; }
    public byte[] getImageData() { return imageData; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
