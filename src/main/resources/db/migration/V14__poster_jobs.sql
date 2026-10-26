CREATE TABLE poster_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    direction VARCHAR(300) NOT NULL,
    advice VARCHAR(300) NOT NULL,
    requested_count INT NOT NULL,
    material_snapshot LONGTEXT NOT NULL,
    checks_json TEXT NOT NULL,
    error_message VARCHAR(500) NULL,
    selected_indices VARCHAR(20) NOT NULL DEFAULT '',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_poster_jobs_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    UNIQUE KEY uq_poster_jobs_request_key (request_key),
    INDEX idx_poster_jobs_task_id_id (task_id, id)
);

CREATE TABLE poster_job_images (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    position INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    mime VARCHAR(30) NOT NULL,
    image_data LONGBLOB NOT NULL,
    logo_applied BOOLEAN NOT NULL DEFAULT FALSE,
    review_json VARCHAR(200) NOT NULL DEFAULT '{}',
    CONSTRAINT fk_poster_job_images_job FOREIGN KEY (job_id) REFERENCES poster_jobs (id) ON DELETE CASCADE,
    UNIQUE KEY uq_poster_job_images_position (job_id, position)
);

CREATE TABLE poster_job_logos (
    job_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    mime VARCHAR(30) NOT NULL,
    image_data LONGBLOB NOT NULL,
    PRIMARY KEY (job_id, material_id),
    CONSTRAINT fk_poster_job_logos_job FOREIGN KEY (job_id) REFERENCES poster_jobs (id) ON DELETE CASCADE
);
