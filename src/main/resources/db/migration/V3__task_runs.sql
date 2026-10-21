CREATE TABLE task_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_summary VARCHAR(200) NOT NULL,
    last_error VARCHAR(500) NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_runs_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    INDEX idx_task_runs_task_id_id (task_id, id),
    INDEX idx_task_runs_status (status)
);

CREATE TABLE task_run_steps (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    step_number INT NOT NULL,
    kind VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    tool_name VARCHAR(100) NULL,
    input_summary VARCHAR(500) NOT NULL,
    output_summary VARCHAR(500) NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_run_steps_run FOREIGN KEY (run_id) REFERENCES task_runs (id),
    CONSTRAINT uq_task_run_steps_number UNIQUE (run_id, step_number)
);
