package com.staranaly.arbiter.share;

import java.time.LocalDateTime;

public record PosterMaterialResponse(Long id, String name, PosterMaterial.Type type, String content,
        String sourceName, String mime, LocalDateTime updatedAt) {
    public static PosterMaterialResponse from(PosterMaterial material) {
        return new PosterMaterialResponse(material.getId(), material.getName(), material.getType(),
                material.getContent(), material.getSourceName(), material.getMime(), material.getUpdatedAt());
    }
}
