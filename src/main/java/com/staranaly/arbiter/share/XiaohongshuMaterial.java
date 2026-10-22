package com.staranaly.arbiter.share;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "xiaohongshu_materials")
public class XiaohongshuMaterial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected XiaohongshuMaterial() {}

    public XiaohongshuMaterial(String name, String content, String sourceName) {
        this.createdAt = LocalDateTime.now();
        replace(name, content, sourceName);
    }

    public void replace(String name, String content, String sourceName) {
        this.name = name;
        this.content = content;
        this.sourceName = sourceName;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getContent() { return content; }
    public String getSourceName() { return sourceName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
