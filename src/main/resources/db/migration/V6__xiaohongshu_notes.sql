CREATE TABLE xiaohongshu_notes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    status VARCHAR(20) NOT NULL,
    requirements TEXT NOT NULL,
    title VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    tags_text VARCHAR(300) NOT NULL,
    image_name VARCHAR(255) NOT NULL,
    image_mime VARCHAR(30) NOT NULL,
    image_size INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_xiaohongshu_notes_status_updated (status, updated_at)
);

CREATE TABLE xiaohongshu_note_images (
    note_id BIGINT NOT NULL,
    image_data LONGBLOB NOT NULL,
    PRIMARY KEY (note_id),
    CONSTRAINT fk_xiaohongshu_note_images_note FOREIGN KEY (note_id)
        REFERENCES xiaohongshu_notes (id) ON DELETE CASCADE
);
