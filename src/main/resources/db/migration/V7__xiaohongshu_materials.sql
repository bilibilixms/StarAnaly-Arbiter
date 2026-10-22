CREATE TABLE xiaohongshu_materials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(60) NOT NULL,
    content TEXT NOT NULL,
    source_name VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_xiaohongshu_materials_updated (updated_at)
);
