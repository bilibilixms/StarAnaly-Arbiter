package com.staranaly.arbiter.task;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskImageService {
    private static final int MAX_IMAGES = 6;
    private static final long MAX_IMAGE_BYTES = 20_000_000L;
    private static final long MAX_TOTAL_BYTES = 30_000_000L;
    private final JdbcTemplate jdbc;
    private final TaskRepository tasks;

    public TaskImageService(JdbcTemplate jdbc, TaskRepository tasks) {
        this.jdbc = jdbc;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public List<TaskImageResponse> list(Long taskId) {
        requireTask(taskId);
        return jdbc.query("""
                SELECT id, task_id, name, mime, byte_size, created_at
                FROM task_images WHERE task_id = ? ORDER BY id ASC
                """, (rs, row) -> new TaskImageResponse(rs.getLong("id"), rs.getLong("task_id"),
                rs.getString("name"), rs.getString("mime"), rs.getInt("byte_size"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), taskId);
    }

    @Transactional
    public TaskImageResponse upload(Long taskId, MultipartFile file) {
        requireTask(taskId);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM task_images WHERE task_id = ?", Integer.class, taskId);
        if (count != null && count >= MAX_IMAGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每份需求最多保存 6 张图片。");
        }
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片必须非空且不超过 20 MB。");
        }
        Long total = jdbc.queryForObject("SELECT COALESCE(SUM(byte_size), 0) FROM task_images WHERE task_id = ?",
                Long.class, taskId);
        if ((total == null ? 0L : total) + file.getSize() > MAX_TOTAL_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这份需求的图片总大小不能超过 30 MB。");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取图片。");
        }
        String mime = detectMime(bytes);
        if (mime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只支持 JPEG、PNG、GIF 或 WebP 图片。");
        }
        String name = safeName(file.getOriginalFilename());
        LocalDateTime created = LocalDateTime.now(ZoneOffset.UTC);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO task_images (task_id, name, mime, byte_size, image_data, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, taskId);
            statement.setString(2, name);
            statement.setString(3, mime);
            statement.setInt(4, bytes.length);
            statement.setBytes(5, bytes);
            statement.setTimestamp(6, Timestamp.valueOf(created));
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("Task image ID was not generated");
        return new TaskImageResponse(id.longValue(), taskId, name, mime, bytes.length,
                created.toInstant(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public ImageContent content(Long taskId, Long imageId) {
        requireTask(taskId);
        try {
            return jdbc.queryForObject("""
                    SELECT name, mime, image_data FROM task_images WHERE id = ? AND task_id = ?
                    """, (rs, row) -> new ImageContent(rs.getString("name"), rs.getString("mime"),
                    rs.getBytes("image_data")), imageId, taskId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "这份需求中没有该图片。");
        }
    }

    @Transactional
    public void delete(Long taskId, Long imageId) {
        requireTask(taskId);
        if (jdbc.update("DELETE FROM task_images WHERE id = ? AND task_id = ?", imageId, taskId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "这份需求中没有该图片。");
        }
    }

    private void requireTask(Long taskId) {
        if (!tasks.existsById(taskId)) throw new TaskNotFoundException(taskId);
    }

    private static String safeName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        return name.isEmpty() ? "图片" : name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static String detectMime(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) return "image/png";
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') return "image/gif";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        return null;
    }

    public record ImageContent(String name, String mime, byte[] bytes) {
    }
}
