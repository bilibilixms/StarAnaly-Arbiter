package com.staranaly.arbiter.share;

import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staranaly.arbiter.task.TaskNotFoundException;
import com.staranaly.arbiter.task.TaskRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PosterJobService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TaskRepository tasks;
    private final PosterGenerationService generator;
    private final int dailyImageLimit;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8));
    private final Map<Long, Future<?>> running = new ConcurrentHashMap<>();

    public PosterJobService(JdbcTemplate jdbc, ObjectMapper mapper, TaskRepository tasks, PosterGenerationService generator,
            @Value("${poster.daily-image-limit:40}") int dailyImageLimit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tasks = tasks;
        this.generator = generator;
        this.dailyImageLimit = Math.max(0, dailyImageLimit);
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverInterruptedJobs() {
        jdbc.update("UPDATE poster_jobs SET status = 'FAILED', error_message = '服务重启中断了生成，请重试。', updated_at = NOW(6) WHERE status IN ('PENDING', 'RUNNING')");
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }

    public List<Job> list(long taskId) {
        ensureTask(taskId);
        List<Long> ids = jdbc.query("SELECT id FROM poster_jobs WHERE task_id = ? ORDER BY id ASC",
                (rs, row) -> rs.getLong(1), taskId);
        return ids.stream().map(id -> get(taskId, id)).toList();
    }

    public Job get(long taskId, long jobId) {
        ensureTask(taskId);
        List<Job> rows = jdbc.query("SELECT * FROM poster_jobs WHERE id = ? AND task_id = ?",
                (rs, row) -> {
                    List<Image> images = jdbc.query("SELECT id, position, name, mime, logo_applied, review_json FROM poster_job_images WHERE job_id = ? ORDER BY position",
                            (image, ignored) -> new Image(image.getLong("id"), image.getInt("position"),
                                    image.getString("name"), image.getString("mime"), image.getBoolean("logo_applied"),
                                    image.getString("review_json")), jobId);
                    return new Job(rs.getLong("id"), rs.getString("status"), rs.getString("direction"),
                            rs.getString("advice"), rs.getString("material_snapshot"), rs.getString("checks_json"),
                            rs.getString("error_message"), parseSelection(rs.getString("selected_indices")), images,
                            rs.getInt("requested_count"), rs.getTimestamp("created_at").toLocalDateTime());
                }, jobId, taskId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "海报版本不存在。");
        return rows.getFirst();
    }

    public ImageData image(long taskId, long jobId, long imageId) {
        get(taskId, jobId);
        List<ImageData> rows = jdbc.query("SELECT mime, image_data FROM poster_job_images WHERE id = ? AND job_id = ?",
                (rs, row) -> new ImageData(rs.getString("mime"), rs.getBytes("image_data")), imageId, jobId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "海报图片不存在。");
        return rows.getFirst();
    }

    public ImageData logo(long taskId, long jobId, long materialId) {
        get(taskId, jobId);
        List<ImageData> rows = jdbc.query("SELECT mime, image_data FROM poster_job_logos WHERE job_id = ? AND material_id = ?",
                (rs, row) -> new ImageData(rs.getString("mime"), rs.getBytes("image_data")), jobId, materialId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "原始 Logo 不存在。");
        return rows.getFirst();
    }

    public synchronized Job submit(long taskId, String requestKey, String direction, String advice, int count,
            MultipartFile referenceImage, List<PosterMaterial> materials, String requiredText, String forbiddenContent) {
        validateKey(requestKey);
        List<Job> existing = jdbc.query("SELECT id FROM poster_jobs WHERE request_key = ?",
                (rs, row) -> get(taskId, rs.getLong(1)), requestKey);
        if (!existing.isEmpty()) return existing.getFirst();
        if (!generator.configured()) throw new XiaohongshuInputException("图像 AI 尚未配置接口地址或密钥。");
        if (count < 1 || count > 4) throw new XiaohongshuInputException("一次只能生成 1 至 4 张海报。");
        ensureTask(taskId);
        if (quota().usedToday() + count > dailyImageLimit)
            throw new XiaohongshuInputException("今日 AI 图片生成额度不足，请明天再试或调整 POSTER_DAILY_IMAGE_LIMIT。");
        String subject = validateDirection(direction);
        String revision = validateAdvice(advice);
        Checks checks = checks(requiredText, forbiddenContent, materials);
        byte[] referenceBytes = readReference(referenceImage);
        String referenceName = referenceImage == null ? null : referenceImage.getOriginalFilename();
        String referenceMime = referenceBytes == null ? null : XiaohongshuDraftService.detectMimeType(referenceBytes);
        List<PosterMaterial> chosen = List.copyOf(materials);
        long jobId;
        try { jobId = insert(taskId, requestKey, subject, revision, count, snapshot(chosen), json(checks), "PENDING", "AI"); }
        catch (DuplicateKeyException exception) { return existing(taskId, requestKey); }
        try {
            recordUsage(count);
            saveLogoSnapshots(jobId, chosen);
        } catch (RuntimeException exception) {
            fail(jobId, "准备生成任务失败，请重试。");
            throw exception;
        }
        try {
            FutureTask<Void> future = new FutureTask<>(() -> {
                run(jobId, taskId, subject, revision, count, referenceBytes, referenceName, referenceMime, chosen);
                return null;
            });
            running.put(jobId, future);
            executor.execute(future);
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            running.remove(jobId);
            fail(jobId, "生成队列已满，请稍后重试。");
        }
        return get(taskId, jobId);
    }

    public Job importBatch(long taskId, String requestKey, String direction, String advice,
            List<MultipartFile> files, List<PosterMaterial> materials, String requiredText, String forbiddenContent) {
        validateKey(requestKey);
        List<Long> existing = jdbc.query("SELECT id FROM poster_jobs WHERE request_key = ?", (rs, row) -> rs.getLong(1), requestKey);
        if (!existing.isEmpty()) return get(taskId, existing.getFirst());
        ensureTask(taskId);
        if (files == null || files.isEmpty() || files.size() > 4) throw new XiaohongshuInputException("请选择 1 至 4 张海报。");
        List<StoredImage> images = new ArrayList<>();
        for (MultipartFile file : files) {
            byte[] bytes = readImage(file);
            images.add(new StoredImage(file.getOriginalFilename(), XiaohongshuDraftService.detectMimeType(bytes), bytes));
        }
        long jobId;
        try {
            jobId = insert(taskId, requestKey, validateDirection(direction), validateAdvice(advice), files.size(),
                    snapshot(materials), json(checks(requiredText, forbiddenContent, materials)), "PENDING", "TEMPLATE");
        } catch (DuplicateKeyException exception) { return existing(taskId, requestKey); }
        try { saveLogoSnapshots(jobId, materials); }
        catch (RuntimeException exception) {
            fail(jobId, "保存 Logo 素材失败，请重试。");
            throw exception;
        }
        saveImages(jobId, images);
        return get(taskId, jobId);
    }

    public void cancel(long taskId, long jobId) {
        Job job = get(taskId, jobId);
        if (!"PENDING".equals(job.status()) && !"RUNNING".equals(job.status()))
            throw new XiaohongshuInputException("该任务已经结束，无法取消。");
        jdbc.update("UPDATE poster_jobs SET status = 'CANCELLED', error_message = '已取消生成。', updated_at = NOW(6) WHERE id = ? AND status IN ('PENDING', 'RUNNING')", jobId);
        Future<?> future = running.remove(jobId);
        if (future != null) future.cancel(true);
    }

    public Quota quota() {
        List<Integer> rows = jdbc.query("SELECT image_count FROM poster_daily_usage WHERE usage_day = ?",
                (rs, row) -> rs.getInt(1), LocalDate.now(ZoneId.of("Asia/Shanghai")));
        return new Quota(dailyImageLimit, rows.isEmpty() ? 0 : rows.getFirst());
    }

    private void recordUsage(int count) {
        jdbc.update("""
                INSERT INTO poster_daily_usage (usage_day, image_count) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE image_count = image_count + VALUES(image_count)
                """, LocalDate.now(ZoneId.of("Asia/Shanghai")), count);
    }

    public void delete(long taskId, long jobId) {
        Job job = get(taskId, jobId);
        if ("PENDING".equals(job.status()) || "RUNNING".equals(job.status()))
            throw new XiaohongshuInputException("请先取消生成任务，再删除版本。");
        jdbc.update("DELETE FROM poster_jobs WHERE id = ? AND task_id = ?", jobId, taskId);
    }

    public void replaceImage(long taskId, long jobId, long imageId, MultipartFile file) {
        Job job = get(taskId, jobId);
        if (!"SUCCEEDED".equals(job.status()) || job.images().stream().noneMatch(image -> image.id() == imageId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "海报图片不存在。");
        byte[] bytes = readImage(file);
        if (!"image/png".equals(XiaohongshuDraftService.detectMimeType(bytes)))
            throw new XiaohongshuInputException("叠加 Logo 后请上传 PNG 图片。");
        jdbc.update("UPDATE poster_job_images SET image_data = ?, mime = 'image/png', name = ?, logo_applied = TRUE, review_json = '{}' WHERE id = ? AND job_id = ?",
                bytes, file.getOriginalFilename(), imageId, jobId);
    }

    public void select(long taskId, long jobId, List<Integer> positions) {
        Job job = get(taskId, jobId);
        if (!"SUCCEEDED".equals(job.status())) throw new XiaohongshuInputException("海报尚未生成完成。");
        if (positions == null || positions.size() > 4 || positions.stream().anyMatch(i -> i == null || i < 0
                || job.images().stream().noneMatch(image -> image.position() == i))
                || positions.stream().distinct().count() != positions.size()) {
            throw new XiaohongshuInputException("海报选择无效。");
        }
        jdbc.update("UPDATE poster_jobs SET selected_indices = ?, updated_at = NOW(6) WHERE id = ?",
                positions.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")), jobId);
    }

    public void review(long taskId, long jobId, long imageId, Review review) {
        Job job = get(taskId, jobId);
        if (job.images().stream().noneMatch(image -> image.id() == imageId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "海报图片不存在。");
        jdbc.update("UPDATE poster_job_images SET review_json = ? WHERE id = ? AND job_id = ?",
                json(review), imageId, jobId);
    }

    private void run(long jobId, long taskId, String direction, String advice, int count, byte[] referenceBytes,
            String referenceName, String referenceMime, List<PosterMaterial> materials) {
        try {
            if (jdbc.update("UPDATE poster_jobs SET status = 'RUNNING', updated_at = NOW(6) WHERE id = ? AND status = 'PENDING'", jobId) == 0) return;
            MultipartFile reference = referenceBytes == null ? null : new ByteArrayMultipartFile(
                    referenceName, referenceMime, referenceBytes);
            List<PosterGenerationService.GeneratedPoster> generated = generator.generate(taskId, direction, advice,
                    count, reference, materials);
            List<StoredImage> images = generated.stream().map(item -> new StoredImage(item.name(), item.mime(),
                    Base64.getDecoder().decode(item.base64()))).toList();
            if (isCancelled(jobId)) return;
            saveImages(jobId, images);
        } catch (Exception exception) {
            String message = exception instanceof ResponseStatusException response ? response.getReason() : exception.getMessage();
            fail(jobId, message == null || message.isBlank() ? "生成失败，请稍后重试。" : message);
        } finally {
            running.remove(jobId);
        }
    }

    private boolean isCancelled(long jobId) {
        String status = jdbc.queryForObject("SELECT status FROM poster_jobs WHERE id = ?", String.class, jobId);
        return "CANCELLED".equals(status);
    }

    private void saveImages(long jobId, List<StoredImage> images) {
        try {
            for (int i = 0; i < images.size(); i++) {
                StoredImage image = images.get(i);
                jdbc.update("INSERT INTO poster_job_images (job_id, position, name, mime, image_data, review_json) VALUES (?, ?, ?, ?, ?, '{}')",
                        jobId, i, image.name(), image.mime(), image.bytes());
            }
            int updated = jdbc.update("UPDATE poster_jobs SET status = 'SUCCEEDED', selected_indices = ?, updated_at = NOW(6) WHERE id = ? AND status IN ('PENDING', 'RUNNING')",
                    java.util.stream.IntStream.range(0, images.size()).mapToObj(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(",")), jobId);
            if (updated == 0) jdbc.update("DELETE FROM poster_job_images WHERE job_id = ?", jobId);
        } catch (Exception exception) {
            jdbc.update("DELETE FROM poster_job_images WHERE job_id = ?", jobId);
            fail(jobId, "保存生成图片失败，请重试。");
        }
    }

    private void saveLogoSnapshots(long jobId, List<PosterMaterial> materials) {
        for (PosterMaterial material : materials) {
            if (material.getType() != PosterMaterial.Type.LOGO) continue;
            jdbc.update("INSERT INTO poster_job_logos (job_id, material_id, mime, image_data) VALUES (?, ?, ?, ?)",
                    jobId, material.getId(), material.getMime(), material.getImageData());
        }
    }

    private void fail(long jobId, String message) {
        jdbc.update("UPDATE poster_jobs SET status = 'FAILED', error_message = ?, updated_at = NOW(6) WHERE id = ? AND status <> 'CANCELLED'",
                message.substring(0, Math.min(500, message.length())), jobId);
    }

    private long insert(long taskId, String key, String direction, String advice, int count, String materialSnapshot,
            String checks, String status, String source) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO poster_jobs (task_id, request_key, status, direction, advice, requested_count,
                        material_snapshot, checks_json, source, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                LocalDateTime now = LocalDateTime.now();
                statement.setLong(1, taskId); statement.setString(2, key); statement.setString(3, status);
                statement.setString(4, direction); statement.setString(5, advice);
                statement.setInt(6, count); statement.setString(7, materialSnapshot); statement.setString(8, checks);
                statement.setString(9, source); statement.setObject(10, now); statement.setObject(11, now);
                return statement;
        }, holder);
        return holder.getKey().longValue();
    }

    private Job existing(long taskId, String key) {
        Long id = jdbc.queryForObject("SELECT id FROM poster_jobs WHERE request_key = ?", Long.class, key);
        return get(taskId, id);
    }

    private void ensureTask(long taskId) {
        if (!tasks.existsById(taskId)) throw new TaskNotFoundException(taskId);
    }

    private static String validateDirection(String direction) {
        String value = direction == null ? "" : direction.strip();
        if (value.isBlank() || value.length() > 300) throw new XiaohongshuInputException("请填写不超过 300 字的海报主题。");
        return value;
    }

    private static String validateAdvice(String advice) {
        String value = advice == null ? "" : advice.strip();
        if (value.length() > 300) throw new XiaohongshuInputException("修改方向不能超过 300 字。");
        return value;
    }

    private static void validateKey(String key) {
        try { UUID.fromString(key); } catch (Exception exception) { throw new XiaohongshuInputException("生成请求标识无效。"); }
    }

    private static Checks checks(String required, String forbidden, List<PosterMaterial> materials) {
        String requiredValue = required == null ? "" : required.strip();
        String forbiddenValue = forbidden == null ? "" : forbidden.strip();
        if (requiredValue.length() > 500 || forbiddenValue.length() > 500)
            throw new XiaohongshuInputException("核对要求每项不能超过 500 字。");
        String logo = materials.stream().filter(item -> item.getType() == PosterMaterial.Type.LOGO)
                .map(PosterMaterial::getName).collect(java.util.stream.Collectors.joining("、"));
        List<Long> logoIds = materials.stream().filter(item -> item.getType() == PosterMaterial.Type.LOGO)
                .map(PosterMaterial::getId).toList();
        return new Checks(requiredValue, forbiddenValue, logo, logoIds);
    }

    private String snapshot(List<PosterMaterial> materials) {
        return json(materials.stream().map(item -> Map.of("id", item.getId(), "name", item.getName(), "type", item.getType().name(),
                "content", item.getContent())).toList());
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("无法保存海报记录。", exception); }
    }

    private static byte[] readReference(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > 10_000_000) throw new XiaohongshuInputException("参考图片不能超过 10 MB。");
        return readImage(file);
    }

    private static byte[] readImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 20_000_000)
            throw new XiaohongshuInputException("海报图片无效或超过 20 MB。");
        try {
            byte[] bytes = file.getBytes();
            if (XiaohongshuDraftService.detectMimeType(bytes) == null)
                throw new XiaohongshuInputException("图片仅支持 JPEG、PNG、GIF 或 WebP。");
            return bytes;
        } catch (IOException exception) { throw new XiaohongshuInputException("无法读取图片。"); }
    }

    private static List<Integer> parseSelection(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(Integer::parseInt).toList();
    }

    public record Job(long id, String status, String direction, String advice, String materialSnapshot,
            String checksJson, String error, List<Integer> selected, List<Image> images, int requestedCount,
            LocalDateTime createdAt) {}
    public record Image(long id, int position, String name, String mime, boolean logoApplied, String reviewJson) {}
    public record ImageData(String mime, byte[] bytes) {}
    public record Checks(String requiredText, String forbiddenContent, String logoName, List<Long> logoIds) {}
    public record Review(boolean requiredText, boolean forbiddenContent, boolean logo) {}
    public record Quota(int dailyLimit, int usedToday) {}
    private record StoredImage(String name, String mime, byte[] bytes) {}

    private record ByteArrayMultipartFile(String filename, String mime, byte[] bytes) implements MultipartFile {
        public String getName() { return "referenceImage"; }
        public String getOriginalFilename() { return filename; }
        public String getContentType() { return mime; }
        public boolean isEmpty() { return bytes.length == 0; }
        public long getSize() { return bytes.length; }
        public byte[] getBytes() { return bytes; }
        public InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        public void transferTo(java.io.File destination) throws IOException { java.nio.file.Files.write(destination.toPath(), bytes); }
    }
}
