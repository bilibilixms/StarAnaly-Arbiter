CREATE TABLE poster_materials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(60) NOT NULL,
    type VARCHAR(20) NOT NULL,
    content TEXT NULL,
    source_name VARCHAR(255) NULL,
    mime VARCHAR(30) NULL,
    image_data LONGBLOB NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_poster_materials_updated (updated_at)
);
