package com.staranaly.arbiter.share;

import java.time.LocalDateTime;

public record DouyinMaterialResponse(Long id, String name, String content, String sourceName,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static DouyinMaterialResponse from(DouyinMaterial material) {
        return new DouyinMaterialResponse(material.getId(), material.getName(), material.getContent(),
                material.getSourceName(), material.getCreatedAt(), material.getUpdatedAt());
    }
}
