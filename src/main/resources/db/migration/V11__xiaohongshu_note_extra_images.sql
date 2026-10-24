CREATE TABLE xiaohongshu_note_extra_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    note_id BIGINT NOT NULL,
    position INT NOT NULL,
    image_name VARCHAR(255) NOT NULL,
    image_mime VARCHAR(30) NOT NULL,
    image_size INT NOT NULL,
    image_data LONGBLOB NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_xiaohongshu_extra_images_note FOREIGN KEY (note_id)
        REFERENCES xiaohongshu_notes (id) ON DELETE CASCADE,
    CONSTRAINT uq_xiaohongshu_extra_images_position UNIQUE (note_id, position)
);
