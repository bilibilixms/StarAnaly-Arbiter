CREATE TABLE task_csv_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    byte_size INT NOT NULL,
    row_count INT NOT NULL,
    headers_json TEXT NOT NULL,
    content LONGBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_csv_files_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    INDEX idx_task_csv_files_task_id_id (task_id, id)
);

CREATE TABLE task_csv_analysis_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    result_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_csv_analysis_run FOREIGN KEY (run_id) REFERENCES task_runs (id),
    CONSTRAINT fk_task_csv_analysis_file FOREIGN KEY (file_id) REFERENCES task_csv_files (id),
    INDEX idx_task_csv_analysis_run_id_id (run_id, id)
);
