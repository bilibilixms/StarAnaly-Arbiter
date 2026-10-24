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
public class DouyinNoteService {
    private static final long MAX_IMAGE_BYTES = 20_000_000L;
    private final DouyinNoteRepository notes;
    private final DouyinNoteImageRepository images;
    private final DouyinNoteExtraImageRepository extraImages;
    private final TaskRepository tasks;

    public DouyinNoteService(DouyinNoteRepository notes, DouyinNoteImageRepository images,
            DouyinNoteExtraImageRepository extraImages, TaskRepository tasks) {
        this.notes = notes;
        this.images = images;
        this.extraImages = extraImages;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public List<DouyinNoteResponse> list(DouyinNote.Status status) {
        return notes.findByStatusOrderByUpdatedAtDescIdDesc(status).stream()
                .map(DouyinNoteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DouyinNoteResponse> listByTask(Long taskId) {
        if (!tasks.existsById(taskId)) throw new TaskNotFoundException(taskId);
        return notes.findByTaskIdOrderByUpdatedAtDescIdDesc(taskId).stream()
                .map(DouyinNoteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DouyinNoteResponse get(long id) {
        return DouyinNoteResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public ImageContent image(long id) {
        DouyinNote note = find(id);
        DouyinNoteImage image = images.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在。"));
        return new ImageContent(image.getImageData(), note.getImageMime());
    }

    @Transactional(readOnly = true)
    public List<ImageInfo> imageInfos(long id) {
        DouyinNote note = find(id);
        List<ImageInfo> result = new java.util.ArrayList<>();
        result.add(new ImageInfo(0L, note.getImageName(), note.getImageMime(), note.getImageSize()));
        extraImages.findByNoteIdOrderByPositionAsc(id).forEach(extra ->
                result.add(new ImageInfo(extra.getId(), extra.getImageName(), extra.getImageMime(), extra.getImageSize())));
        return result;
    }

    @Transactional(readOnly = true)
    public ImageContent extraImage(long noteId, long imageId) {
        find(noteId);
        return extraImages.findById(imageId)
                .filter(extra -> extra.getNoteId().equals(noteId))
                .map(extra -> new ImageContent(extra.getImageData(), extra.getImageMime()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在。"));
    }

    @Transactional
    public DouyinNoteResponse create(MultipartFile image, String requirements,
            String title, String body, String tagsText) {
        return create(image, requirements, title, body, tagsText, null);
    }

    @Transactional
    public DouyinNoteResponse create(MultipartFile image, String requirements,
            String title, String body, String tagsText, Long taskId) {
        return create(image, requirements, title, body, tagsText, taskId, null);
    }

    @Transactional
    public DouyinNoteResponse create(MultipartFile image, String requirements,
            String title, String body, String tagsText, Long taskId, Integer directionIndex) {
        return create(image, List.of(), requirements, title, body, tagsText, taskId, directionIndex);
    }

    @Transactional
    public DouyinNoteResponse create(MultipartFile image, List<MultipartFile> extra,
            String requirements, String title, String body, String tagsText, Long taskId, Integer directionIndex) {
        requireTask(taskId);
        TextContent text = validateText(requirements, title, body, tagsText);
        ImageContent content = validateImage(image);
        List<ImageContent> extraContent = validateExtras(extra);
        DouyinNote note = notes.save(new DouyinNote(text.requirements(), text.title(),
                text.body(), text.tagsText(), safeName(image), content.mime(), content.bytes().length,
                taskId, directionIndex));
        images.save(new DouyinNoteImage(note.getId(), content.bytes()));
        saveExtras(note.getId(), extra, extraContent);
        return DouyinNoteResponse.from(note);
    }

    @Transactional
    public DouyinNoteResponse update(long id, MultipartFile image, String requirements,
            String title, String body, String tagsText) {
        return update(id, image, requirements, title, body, tagsText, null);
    }

    @Transactional
    public DouyinNoteResponse update(long id, MultipartFile image, String requirements,
            String title, String body, String tagsText, Long taskId) {
        return update(id, image, requirements, title, body, tagsText, taskId, null);
    }

    @Transactional
    public DouyinNoteResponse update(long id, MultipartFile image, String requirements,
            String title, String body, String tagsText, Long taskId, Integer directionIndex) {
        return update(id, image, null, requirements, title, body, tagsText, taskId, directionIndex);
    }

    @Transactional
    public DouyinNoteResponse update(long id, MultipartFile image, List<MultipartFile> extra,
            String requirements, String title, String body, String tagsText, Long taskId, Integer directionIndex) {
        DouyinNote note = findDraft(id);
        if (taskId != null && note.getTaskId() != null && !taskId.equals(note.getTaskId())) {
            throw new DouyinInputException("文案已关联另一份需求。");
        }
        if (taskId != null && note.getTaskId() == null) {
            requireTask(taskId);
            note.setTaskId(taskId);
        }
        if (directionIndex != null) note.setDirectionIndex(directionIndex);
        TextContent text = validateText(requirements, title, body, tagsText);
        List<ImageContent> extraContent = extra == null ? null : validateExtras(extra);
        note.updateText(text.requirements(), text.title(), text.body(), text.tagsText());
        if (image != null && !image.isEmpty()) {
            ImageContent content = validateImage(image);
            note.updateImage(safeName(image), content.mime(), content.bytes().length);
            DouyinNoteImage old = images.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在。"));
            old.setImageData(content.bytes());
            extraImages.deleteByNoteId(id);
            extraImages.flush();
            if (extraContent != null) saveExtras(id, extra, extraContent);
        }
        notes.flush();
        return DouyinNoteResponse.from(note);
    }

    @Transactional
    public DouyinNoteResponse markPublished(long id) {
        DouyinNote note = find(id);
        if (note.getStatus() == DouyinNote.Status.DRAFT) {
            note.markPublished();
            if (note.getTaskId() != null) {
                tasks.findById(note.getTaskId()).ifPresent(task ->
                        task.updatePlan(TaskWorkflowStage.PUBLISHED, task.getDiscussionSummary()));
            }
            notes.flush();
        }
        return DouyinNoteResponse.from(note);
    }

    private void requireTask(Long taskId) {
        if (taskId != null && !tasks.existsById(taskId)) throw new TaskNotFoundException(taskId);
    }

    @Transactional
    public void delete(long id) {
        findDraft(id);
        extraImages.deleteByNoteId(id);
        images.deleteById(id);
        notes.deleteById(id);
    }

    private DouyinNote find(long id) {
        return notes.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "文案不存在。"));
    }

    private DouyinNote findDraft(long id) {
        DouyinNote note = find(id);
        if (note.getStatus() != DouyinNote.Status.DRAFT) {
            throw new DouyinInputException("已发布的文案不能修改或删除。");
        }
        return note;
    }

    private static TextContent validateText(String requirements, String title, String body, String tagsText) {
        String prompt = requirements == null ? "" : requirements.strip();
        String heading = title == null ? "" : title.strip();
        String article = body == null ? "" : body.strip();
        String tags = tagsText == null ? "" : tagsText.strip();
        if (prompt.length() > 2000) {
            throw new DouyinInputException("文字要求不能超过 2000 字。");
        }
        if (heading.isEmpty() || heading.codePointCount(0, heading.length()) > 20) {
            throw new DouyinInputException("标题需为 1 至 20 字。");
        }
        String completeBody = tags.isEmpty() ? article : article + "\n\n" + tags;
        if (article.isEmpty() || article.length() > 5000 || tags.length() > 300
                || completeBody.codePointCount(0, completeBody.length()) > 1000) {
            throw new DouyinInputException("正文与话题合计不能超过 1000 字。");
        }
        return new TextContent(prompt, heading, article, tags);
    }

    private static ImageContent validateImage(MultipartFile image) {
        if (image == null || image.isEmpty() || image.getSize() > MAX_IMAGE_BYTES) {
            throw new DouyinInputException("请选择一张不超过 20 MB 的图片。");
        }
        try {
            byte[] bytes = image.getBytes();
            String mime = DouyinDraftService.detectMimeType(bytes);
            if (mime == null) {
                throw new DouyinInputException("图片仅支持 JPEG、PNG、GIF 或 WebP 格式。");
            }
            return new ImageContent(bytes, mime);
        } catch (IOException exception) {
            throw new DouyinInputException("无法读取上传的图片，请重新选择。");
        }
    }

    private static String safeName(MultipartFile image) {
        String name = image.getOriginalFilename();
        if (name == null || name.isBlank()) return "image";
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static List<ImageContent> validateExtras(List<MultipartFile> extra) {
        if (extra == null) return List.of();
        if (extra.size() > 3) throw new DouyinInputException("每篇图文最多保存 4 张图片。");
        return extra.stream().map(DouyinNoteService::validateImage).toList();
    }

    private void saveExtras(long noteId, List<MultipartFile> extra, List<ImageContent> contents) {
        for (int i = 0; i < contents.size(); i++) {
            ImageContent content = contents.get(i);
            extraImages.save(new DouyinNoteExtraImage(noteId, i + 1, safeName(extra.get(i)),
                    content.mime(), content.bytes()));
        }
    }

    private record TextContent(String requirements, String title, String body, String tagsText) {}
    public record ImageContent(byte[] bytes, String mime) {}
    public record ImageInfo(Long id, String name, String mime, int byteSize) {}
}
