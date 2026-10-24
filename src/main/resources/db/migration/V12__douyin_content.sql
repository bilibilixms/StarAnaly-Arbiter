CREATE TABLE douyin_notes (
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
    INDEX idx_douyin_notes_status_updated (status, updated_at)
);

CREATE TABLE douyin_note_images (
    note_id BIGINT NOT NULL,
    image_data LONGBLOB NOT NULL,
    PRIMARY KEY (note_id),
    CONSTRAINT fk_douyin_note_images_note FOREIGN KEY (note_id)
        REFERENCES douyin_notes (id) ON DELETE CASCADE
);

CREATE TABLE douyin_materials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(60) NOT NULL,
    content TEXT NOT NULL,
    source_name VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_douyin_materials_updated (updated_at)
);

CREATE TABLE douyin_note_extra_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    note_id BIGINT NOT NULL,
    position INT NOT NULL,
    image_name VARCHAR(255) NOT NULL,
    image_mime VARCHAR(30) NOT NULL,
    image_size INT NOT NULL,
    image_data LONGBLOB NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_douyin_extra_images_note FOREIGN KEY (note_id)
        REFERENCES douyin_notes (id) ON DELETE CASCADE,
    CONSTRAINT uq_douyin_extra_images_position UNIQUE (note_id, position)
);

ALTER TABLE douyin_notes
    ADD COLUMN task_id BIGINT NULL,
    ADD COLUMN direction_index INT NULL,
    ADD CONSTRAINT fk_douyin_notes_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    ADD INDEX idx_douyin_notes_task_id_id (task_id, id);
