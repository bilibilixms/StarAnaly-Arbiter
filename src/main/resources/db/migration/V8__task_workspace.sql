ALTER TABLE tasks
    ADD COLUMN workflow_stage VARCHAR(24) NOT NULL DEFAULT 'PLANNING',
    ADD COLUMN discussion_summary VARCHAR(5000) NULL;

CREATE TABLE task_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    target VARCHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_links_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    INDEX idx_task_links_task_id_id (task_id, id)
);
