CREATE TABLE task_knowledge_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    byte_size INT NOT NULL,
    chunk_count INT NOT NULL,
    content LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_knowledge_documents_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    INDEX idx_task_knowledge_documents_task_id_id (task_id, id)
);

CREATE TABLE task_knowledge_chunks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    ordinal_number INT NOT NULL,
    start_line INT NOT NULL,
    end_line INT NOT NULL,
    content TEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_knowledge_chunks_document FOREIGN KEY (document_id)
        REFERENCES task_knowledge_documents (id) ON DELETE CASCADE,
    CONSTRAINT uq_task_knowledge_chunks_ordinal UNIQUE (document_id, ordinal_number)
);

CREATE TABLE task_knowledge_citations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    chunk_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_knowledge_citations_run FOREIGN KEY (run_id) REFERENCES task_runs (id),
    CONSTRAINT fk_task_knowledge_citations_chunk FOREIGN KEY (chunk_id)
        REFERENCES task_knowledge_chunks (id) ON DELETE CASCADE,
    CONSTRAINT uq_task_knowledge_citations_run_chunk UNIQUE (run_id, chunk_id),
    INDEX idx_task_knowledge_citations_run_id_id (run_id, id)
);
