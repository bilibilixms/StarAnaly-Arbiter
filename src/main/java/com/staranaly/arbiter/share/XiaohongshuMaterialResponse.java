package com.staranaly.arbiter.share;

import java.time.LocalDateTime;

public record XiaohongshuMaterialResponse(Long id, String name, String content, String sourceName,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static XiaohongshuMaterialResponse from(XiaohongshuMaterial material) {
        return new XiaohongshuMaterialResponse(material.getId(), material.getName(), material.getContent(),
                material.getSourceName(), material.getCreatedAt(), material.getUpdatedAt());
    }
}
