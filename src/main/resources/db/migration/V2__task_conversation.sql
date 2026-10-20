ALTER TABLE tasks
    ADD COLUMN last_error VARCHAR(500) NULL;

CREATE TABLE task_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content VARCHAR(10000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_messages_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    INDEX idx_task_messages_task_id_id (task_id, id)
);
