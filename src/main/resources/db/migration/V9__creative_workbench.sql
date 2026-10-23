CREATE TABLE task_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    mime VARCHAR(30) NOT NULL,
    byte_size INT NOT NULL,
    image_data LONGBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_images_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    INDEX idx_task_images_task_id_id (task_id, id)
);

CREATE TABLE task_briefs (
    task_id BIGINT NOT NULL,
    brief_json LONGTEXT NOT NULL,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    source_fingerprint VARCHAR(64) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id),
    CONSTRAINT fk_task_briefs_task FOREIGN KEY (task_id) REFERENCES tasks (id)
);

ALTER TABLE xiaohongshu_notes
    ADD COLUMN task_id BIGINT NULL,
    ADD COLUMN direction_index INT NULL,
    ADD CONSTRAINT fk_xiaohongshu_notes_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    ADD INDEX idx_xiaohongshu_notes_task_id_id (task_id, id);

UPDATE task_links SET target = 'WORKBENCH' WHERE target = 'GAME';
