package com.staranaly.arbiter.knowledge;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import com.staranaly.arbiter.task.TaskNotFoundException;
import com.staranaly.arbiter.task.TaskRepository;
import com.staranaly.arbiter.task.TaskRunNotFoundException;
import com.staranaly.arbiter.task.TaskRunRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeService {

    private final JdbcTemplate jdbc;
    private final TaskRepository tasks;
    private final TaskRunRepository runs;
    private final KnowledgeTextProcessor processor;
    private final KnowledgeDocumentParser documentParser;

    public KnowledgeService(JdbcTemplate jdbc, TaskRepository tasks, TaskRunRepository runs,
            KnowledgeTextProcessor processor, KnowledgeDocumentParser documentParser) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.runs = runs;
        this.processor = processor;
        this.documentParser = documentParser;
    }

    @Transactional
    public KnowledgeDocumentMetadata upload(Long taskId, MultipartFile file) {
        ensureTask(taskId);
        String name = safeName(file.getOriginalFilename());
        String lower = name.toLowerCase(Locale.ROOT);
        boolean plainText = lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown");
        if (!plainText && !lower.endsWith(".pdf") && !lower.endsWith(".doc") && !lower.endsWith(".docx")) {
            throw new KnowledgeInputException("只支持 TXT、Markdown、PDF 或 Word 资料。");
        }
        int maxBytes = plainText ? KnowledgeTextProcessor.MAX_BYTES : KnowledgeDocumentParser.MAX_BINARY_BYTES;
        if (file.getSize() == 0 || file.getSize() > maxBytes) {
            throw new KnowledgeInputException(plainText ? "文本资料必须非空且不超过 1 MiB。"
                    : "PDF 或 Word 文件必须非空且不超过 5 MiB。");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new KnowledgeInputException("无法读取上传的资料。");
        }
        String content = documentParser.parse(name, bytes);
        List<KnowledgeChunk> chunks = processor.chunks(content);
        LocalDateTime created = LocalDateTime.now(ZoneOffset.UTC);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO task_knowledge_documents
                    (task_id, name, byte_size, chunk_count, content, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, taskId);
            statement.setString(2, name);
            statement.setInt(3, bytes.length);
            statement.setInt(4, chunks.size());
            statement.setString(5, content);
            statement.setTimestamp(6, Timestamp.valueOf(created));
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("Knowledge document ID was not generated");
        }
        Long documentId = id.longValue();
        jdbc.batchUpdate("""
                INSERT INTO task_knowledge_chunks
                (document_id, ordinal_number, start_line, end_line, content)
                VALUES (?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(java.sql.PreparedStatement statement, int index)
                            throws java.sql.SQLException {
                        KnowledgeChunk chunk = chunks.get(index);
                        statement.setLong(1, documentId);
                        statement.setInt(2, chunk.ordinal());
                        statement.setInt(3, chunk.startLine());
                        statement.setInt(4, chunk.endLine());
                        statement.setString(5, chunk.content());
                    }

                    @Override
                    public int getBatchSize() {
                        return chunks.size();
                    }
                });
        return new KnowledgeDocumentMetadata(documentId, taskId, name, bytes.length,
                chunks.size(), created.toInstant(ZoneOffset.UTC));
    }

    public List<KnowledgeDocumentMetadata> list(Long taskId) {
        ensureTask(taskId);
        return jdbc.query("""
                SELECT id, task_id, name, byte_size, chunk_count, created_at
                FROM task_knowledge_documents WHERE task_id = ? ORDER BY id DESC
                """, (rs, row) -> new KnowledgeDocumentMetadata(rs.getLong("id"), rs.getLong("task_id"),
                rs.getString("name"), rs.getInt("byte_size"), rs.getInt("chunk_count"),
                utc(rs.getObject("created_at", LocalDateTime.class))), taskId);
    }

    public KnowledgeSearchResponse search(Long taskId, String query, int limit) {
        ensureTask(taskId);
        processor.rank(List.of(), query, limit);
        List<KnowledgeHit> candidates = jdbc.query("""
                SELECT chunk.id AS chunk_id, document.id AS document_id, document.name AS document_name,
                       chunk.start_line, chunk.end_line, chunk.content
                FROM task_knowledge_chunks chunk
                JOIN task_knowledge_documents document ON document.id = chunk.document_id
                WHERE document.task_id = ?
                """, (rs, row) -> new KnowledgeHit(rs.getLong("chunk_id"), rs.getLong("document_id"),
                rs.getString("document_name"), rs.getInt("start_line"), rs.getInt("end_line"),
                rs.getString("content"), 0), taskId);
        return new KnowledgeSearchResponse(query, processor.rank(candidates, query, limit));
    }

    @Transactional
    public KnowledgeSearchResponse searchForTool(Long taskId, Long runId, String query, int limit) {
        if (runs.findByIdAndTask_Id(runId, taskId).isEmpty()) {
            throw new TaskRunNotFoundException(taskId, runId);
        }
        KnowledgeSearchResponse result = search(taskId, query, limit);
        for (KnowledgeHit hit : result.hits()) {
            jdbc.update("""
                    INSERT IGNORE INTO task_knowledge_citations (run_id, chunk_id, created_at)
                    VALUES (?, ?, ?)
                    """, runId, hit.chunkId(), Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
        }
        return result;
    }

    public List<KnowledgeCitation> citations(Long taskId, Long runId) {
        ensureTask(taskId);
        if (runs.findByIdAndTask_Id(runId, taskId).isEmpty()) {
            throw new TaskRunNotFoundException(taskId, runId);
        }
        return jdbc.query("""
                SELECT citation.id, document.id AS document_id, document.name AS document_name,
                       chunk.start_line, chunk.end_line, chunk.content
                FROM task_knowledge_citations citation
                JOIN task_knowledge_chunks chunk ON chunk.id = citation.chunk_id
                JOIN task_knowledge_documents document ON document.id = chunk.document_id
                WHERE citation.run_id = ? AND document.task_id = ? ORDER BY citation.id
                """, (rs, row) -> new KnowledgeCitation(rs.getLong("id"), rs.getLong("document_id"),
                rs.getString("document_name"), rs.getInt("start_line"), rs.getInt("end_line"),
                rs.getString("content")), runId, taskId);
    }

    @Transactional
    public void delete(Long taskId, Long documentId) {
        ensureTask(taskId);
        int deleted = jdbc.update("DELETE FROM task_knowledge_documents WHERE task_id = ? AND id = ?",
                taskId, documentId);
        if (deleted == 0) {
            throw new KnowledgeDocumentNotFoundException(taskId, documentId);
        }
    }

    private void ensureTask(Long taskId) {
        if (!tasks.existsById(taskId)) {
            throw new TaskNotFoundException(taskId);
        }
    }

    private static Instant utc(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    private static String safeName(String original) {
        if (original == null) {
            throw new KnowledgeInputException("资料文件名不能为空。");
        }
        String normalized = original.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 255 || name.chars().anyMatch(Character::isISOControl)) {
            throw new KnowledgeInputException("资料文件名无效。");
        }
        return name;
    }
}
