package com.staranaly.arbiter.csv;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.task.TaskNotFoundException;
import com.staranaly.arbiter.task.TaskRepository;
import com.staranaly.arbiter.task.TaskRunNotFoundException;
import com.staranaly.arbiter.task.TaskRunRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CsvFileService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CsvParser parser;
    private final CsvAnalyzer analyzer;
    private final TaskRepository tasks;
    private final TaskRunRepository runs;

    public CsvFileService(JdbcTemplate jdbc, ObjectMapper objectMapper, CsvParser parser,
            CsvAnalyzer analyzer, TaskRepository tasks, TaskRunRepository runs) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.parser = parser;
        this.analyzer = analyzer;
        this.tasks = tasks;
        this.runs = runs;
    }

    public CsvFileMetadata upload(Long taskId, MultipartFile file) {
        ensureTask(taskId);
        String name = safeName(file.getOriginalFilename());
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new CsvInputException("只支持 .csv 文件。");
        }
        if (file.getSize() == 0 || file.getSize() > CsvParser.MAX_BYTES) {
            throw new CsvInputException("CSV 文件必须非空且不超过 5 MiB。");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new CsvInputException("无法读取上传的 CSV 文件。");
        }
        CsvData data = parser.parse(bytes);
        String headers = json(data.headers());
        LocalDateTime created = LocalDateTime.now(ZoneOffset.UTC);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO task_csv_files
                    (task_id, name, byte_size, row_count, headers_json, content, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, taskId);
            statement.setString(2, name);
            statement.setInt(3, bytes.length);
            statement.setInt(4, data.rows().size());
            statement.setString(5, headers);
            statement.setBytes(6, bytes);
            statement.setTimestamp(7, Timestamp.valueOf(created));
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("CSV file ID was not generated");
        }
        return new CsvFileMetadata(id.longValue(), taskId, name, bytes.length, data.rows().size(),
                data.headers(), created.toInstant(ZoneOffset.UTC));
    }

    public List<CsvFileMetadata> list(Long taskId) {
        ensureTask(taskId);
        return jdbc.query("""
                SELECT id, task_id, name, byte_size, row_count, headers_json, created_at
                FROM task_csv_files WHERE task_id = ? ORDER BY id DESC
                """, (rs, row) -> metadata(rs), taskId);
    }

    public CsvPreviewResponse preview(Long taskId, Long fileId, int limit) {
        CsvFileMetadata file = find(taskId, fileId);
        CsvData data = parser.parse(content(taskId, fileId));
        return new CsvPreviewResponse(file, analyzer.rows(data, null, null, limit));
    }

    public List<Map<String, String>> rows(Long taskId, Long fileId,
            String filterColumn, String filterValue, int limit) {
        find(taskId, fileId);
        return analyzer.rows(parser.parse(content(taskId, fileId)), filterColumn, filterValue, limit);
    }

    public CsvAggregateResponse aggregate(Long taskId, Long fileId, String groupBy, String sumColumn,
            boolean byMonth, String filterColumn, String filterValue) {
        find(taskId, fileId);
        return analyzer.aggregate(fileId, parser.parse(content(taskId, fileId)), groupBy,
                sumColumn, byMonth, filterColumn, filterValue);
    }

    public void saveAnalysis(Long runId, Long fileId, CsvAggregateResponse result) {
        jdbc.update("""
                INSERT INTO task_csv_analysis_results (run_id, file_id, result_json, created_at)
                VALUES (?, ?, ?, ?)
                """, runId, fileId, json(result), Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
    }

    public List<CsvAnalysisResult> analyses(Long taskId, Long runId) {
        ensureTask(taskId);
        if (runs.findByIdAndTask_Id(runId, taskId).isEmpty()) {
            throw new TaskRunNotFoundException(taskId, runId);
        }
        return jdbc.query("""
                SELECT result.id, result.run_id, result.result_json, result.created_at
                FROM task_csv_analysis_results result
                JOIN task_runs run ON run.id = result.run_id
                WHERE run.task_id = ? AND run.id = ? ORDER BY result.id
                """, (rs, row) -> new CsvAnalysisResult(rs.getLong("id"), rs.getLong("run_id"),
                parseResult(rs.getString("result_json")), utc(rs.getObject("created_at", LocalDateTime.class))),
                taskId, runId);
    }

    private CsvFileMetadata find(Long taskId, Long fileId) {
        ensureTask(taskId);
        return jdbc.query("""
                SELECT id, task_id, name, byte_size, row_count, headers_json, created_at
                FROM task_csv_files WHERE task_id = ? AND id = ?
                """, (rs, row) -> metadata(rs), taskId, fileId).stream().findFirst()
                .orElseThrow(() -> new CsvFileNotFoundException(taskId, fileId));
    }

    private byte[] content(Long taskId, Long fileId) {
        byte[] bytes = jdbc.query("SELECT content FROM task_csv_files WHERE task_id = ? AND id = ?",
                rs -> rs.next() ? rs.getBytes(1) : null, taskId, fileId);
        if (bytes == null) {
            throw new CsvFileNotFoundException(taskId, fileId);
        }
        return bytes;
    }

    private CsvFileMetadata metadata(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            List<String> headers = objectMapper.readValue(rs.getString("headers_json"), new TypeReference<>() {});
            return new CsvFileMetadata(rs.getLong("id"), rs.getLong("task_id"), rs.getString("name"),
                    rs.getInt("byte_size"), rs.getInt("row_count"), headers,
                    utc(rs.getObject("created_at", LocalDateTime.class)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored CSV metadata is invalid", exception);
        }
    }

    private CsvAggregateResponse parseResult(String value) {
        try {
            return objectMapper.readValue(value, CsvAggregateResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored CSV analysis result is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode CSV data", exception);
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
            throw new CsvInputException("CSV 文件名不能为空。");
        }
        String normalized = original.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 255 || name.chars().anyMatch(Character::isISOControl)) {
            throw new CsvInputException("CSV 文件名无效。");
        }
        return name;
    }
}
