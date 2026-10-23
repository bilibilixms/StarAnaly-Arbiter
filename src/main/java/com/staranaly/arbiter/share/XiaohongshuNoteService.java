package com.staranaly.arbiter.share;

import java.io.IOException;
import java.util.List;

import com.staranaly.arbiter.task.TaskNotFoundException;
import com.staranaly.arbiter.task.TaskRepository;
import com.staranaly.arbiter.task.TaskWorkflowStage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class XiaohongshuNoteService {
    private static final long MAX_IMAGE_BYTES = 20_000_000L;
    private final XiaohongshuNoteRepository notes;
    private final XiaohongshuNoteImageRepository images;
    private final TaskRepository tasks;

    public XiaohongshuNoteService(XiaohongshuNoteRepository notes, XiaohongshuNoteImageRepository images,
            TaskRepository tasks) {
        this.notes = notes;
        this.images = images;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public List<XiaohongshuNoteResponse> list(XiaohongshuNote.Status status) {
        return notes.findByStatusOrderByUpdatedAtDescIdDesc(status).stream()
                .map(XiaohongshuNoteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<XiaohongshuNoteResponse> listByTask(Long taskId) {
        if (!tasks.existsById(taskId)) throw new TaskNotFoundException(taskId);
        return notes.findByTaskIdOrderByUpdatedAtDescIdDesc(taskId).stream()
                .map(XiaohongshuNoteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public XiaohongshuNoteResponse get(long id) {
        return XiaohongshuNoteResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public ImageContent image(long id) {
        XiaohongshuNote note = find(id);
        XiaohongshuNoteImage image = images.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在。"));
        return new ImageContent(image.getImageData(), note.getImageMime());
    }

    @Transactional
    public XiaohongshuNoteResponse create(MultipartFile image, String requirements,
            String title, String body, String tagsText) {
        return create(image, requirements, title, body, tagsText, null);
    }

    @Transactional
    public XiaohongshuNoteResponse create(MultipartFile image, String requirements,
            String title, String body, String tagsText, Long taskId) {
        return create(image, requirements, title, body, tagsText, taskId, null);
    }

    @Transactional
    public XiaohongshuNoteResponse create(MultipartFile image, String requirements,
            String title, String body, String tagsText, Long taskId, Integer directionIndex) {
        requireTask(taskId);
        TextContent text = validateText(requirements, title, body, tagsText);
        ImageContent content = validateImage(image);
        XiaohongshuNote note = notes.save(new XiaohongshuNote(text.requirements(), text.title(),
                text.body(), text.tagsText(), safeName(image), content.mime(), content.bytes().length,
                taskId, directionIndex));
        images.save(new XiaohongshuNoteImage(note.getId(), content.bytes()));
        return XiaohongshuNoteResponse.from(note);
    }

    @Transactional
    public XiaohongshuNoteResponse update(long id, MultipartFile image, String requirements,
            String title, String body, String tagsText) {
        return update(id, image, requirements, title, body, tagsText, null);
    }

    @Transactional
    public XiaohongshuNoteResponse update(long id, MultipartFile image, String requirements,
            String title, String body, String tagsText, Long taskId) {
        return update(id, image, requirements, title, body, tagsText, taskId, null);
    }

    @Transactional
    public XiaohongshuNoteResponse update(long id, MultipartFile image, String requirements,
            String title, String body, String tagsText, Long taskId, Integer directionIndex) {
        XiaohongshuNote note = findDraft(id);
        if (taskId != null && note.getTaskId() != null && !taskId.equals(note.getTaskId())) {
            throw new XiaohongshuInputException("文案已关联另一份需求。");
        }
        if (taskId != null && note.getTaskId() == null) {
            requireTask(taskId);
            note.setTaskId(taskId);
        }
        if (directionIndex != null) note.setDirectionIndex(directionIndex);
        TextContent text = validateText(requirements, title, body, tagsText);
        note.updateText(text.requirements(), text.title(), text.body(), text.tagsText());
        if (image != null && !image.isEmpty()) {
            ImageContent content = validateImage(image);
            note.updateImage(safeName(image), content.mime(), content.bytes().length);
            XiaohongshuNoteImage old = images.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在。"));
            old.setImageData(content.bytes());
        }
        notes.flush();
        return XiaohongshuNoteResponse.from(note);
    }

    @Transactional
    public XiaohongshuNoteResponse markPublished(long id) {
        XiaohongshuNote note = find(id);
        if (note.getStatus() == XiaohongshuNote.Status.DRAFT) {
            note.markPublished();
            if (note.getTaskId() != null) {
                tasks.findById(note.getTaskId()).ifPresent(task ->
                        task.updatePlan(TaskWorkflowStage.PUBLISHED, task.getDiscussionSummary()));
            }
            notes.flush();
        }
        return XiaohongshuNoteResponse.from(note);
    }

    private void requireTask(Long taskId) {
        if (taskId != null && !tasks.existsById(taskId)) throw new TaskNotFoundException(taskId);
    }

    @Transactional
    public void delete(long id) {
        findDraft(id);
        images.deleteById(id);
        notes.deleteById(id);
    }

    private XiaohongshuNote find(long id) {
        return notes.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "文案不存在。"));
    }

    private XiaohongshuNote findDraft(long id) {
        XiaohongshuNote note = find(id);
        if (note.getStatus() != XiaohongshuNote.Status.DRAFT) {
            throw new XiaohongshuInputException("已发布的文案不能修改或删除。");
        }
        return note;
    }

    private static TextContent validateText(String requirements, String title, String body, String tagsText) {
        String prompt = requirements == null ? "" : requirements.strip();
        String heading = title == null ? "" : title.strip();
        String article = body == null ? "" : body.strip();
        String tags = tagsText == null ? "" : tagsText.strip();
        if (prompt.length() > 2000) {
            throw new XiaohongshuInputException("文字要求不能超过 2000 字。");
        }
        if (heading.isEmpty() || heading.codePointCount(0, heading.length()) > 20) {
            throw new XiaohongshuInputException("标题需为 1 至 20 字。");
        }
        String completeBody = tags.isEmpty() ? article : article + "\n\n" + tags;
        if (article.isEmpty() || article.length() > 5000 || tags.length() > 300
                || completeBody.codePointCount(0, completeBody.length()) > 1000) {
            throw new XiaohongshuInputException("正文与话题合计不能超过 1000 字。");
        }
        return new TextContent(prompt, heading, article, tags);
    }

    private static ImageContent validateImage(MultipartFile image) {
        if (image == null || image.isEmpty() || image.getSize() > MAX_IMAGE_BYTES) {
            throw new XiaohongshuInputException("请选择一张不超过 20 MB 的图片。");
        }
        try {
            byte[] bytes = image.getBytes();
            String mime = XiaohongshuDraftService.detectMimeType(bytes);
            if (mime == null) {
                throw new XiaohongshuInputException("图片仅支持 JPEG、PNG、GIF 或 WebP 格式。");
            }
            return new ImageContent(bytes, mime);
        } catch (IOException exception) {
            throw new XiaohongshuInputException("无法读取上传的图片，请重新选择。");
        }
    }

    private static String safeName(MultipartFile image) {
        String name = image.getOriginalFilename();
        if (name == null || name.isBlank()) return "image";
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private record TextContent(String requirements, String title, String body, String tagsText) {}
    public record ImageContent(byte[] bytes, String mime) {}
}
