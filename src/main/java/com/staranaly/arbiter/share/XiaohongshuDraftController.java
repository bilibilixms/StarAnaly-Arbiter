package com.staranaly.arbiter.share;

import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/share/xiaohongshu")
public class XiaohongshuDraftController {

    private final XiaohongshuDraftService service;
    private final XiaohongshuNoteService noteService;
    private final XiaohongshuMaterialService materialService;

    public XiaohongshuDraftController(XiaohongshuDraftService service, XiaohongshuNoteService noteService,
            XiaohongshuMaterialService materialService) {
        this.service = service;
        this.noteService = noteService;
        this.materialService = materialService;
    }

    @PostMapping(path = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public XiaohongshuDraftResponse generate(@RequestParam("image") MultipartFile image,
            @RequestParam(required = false) List<MultipartFile> extraImages,
            @RequestParam(value = "requirements", defaultValue = "") String requirements,
            @RequestParam(value = "materialIds", required = false) List<Long> materialIds,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) Integer direction) {
        return service.generate(image, extraImages, requirements, materialService.selected(materialIds), taskId, direction);
    }

    @GetMapping("/materials")
    public List<XiaohongshuMaterialResponse> materials() {
        return materialService.list();
    }

    @PostMapping(path = "/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public XiaohongshuMaterialResponse createMaterial(@RequestParam String name,
            @RequestParam(required = false) String content, @RequestParam(required = false) MultipartFile file) {
        return materialService.create(name, content, file);
    }

    @PutMapping(path = "/materials/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public XiaohongshuMaterialResponse updateMaterial(@PathVariable long id, @RequestParam String name,
            @RequestParam(required = false) String content, @RequestParam(required = false) MultipartFile file) {
        return materialService.update(id, name, content, file);
    }

    @DeleteMapping("/materials/{id}")
    public ResponseEntity<Void> deleteMaterial(@PathVariable long id) {
        materialService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notes")
    public List<XiaohongshuNoteResponse> notes(@RequestParam XiaohongshuNote.Status status) {
        return noteService.list(status);
    }

    @GetMapping("/tasks/{taskId}/notes")
    public List<XiaohongshuNoteResponse> notesByTask(@PathVariable Long taskId) {
        return noteService.listByTask(taskId);
    }

    @GetMapping("/notes/{id}")
    public XiaohongshuNoteResponse note(@PathVariable long id) {
        return noteService.get(id);
    }

    @GetMapping("/notes/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        XiaohongshuNoteService.ImageContent image = noteService.image(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.mime())).body(image.bytes());
    }

    @GetMapping("/notes/{id}/images")
    public List<XiaohongshuNoteService.ImageInfo> images(@PathVariable long id) {
        return noteService.imageInfos(id);
    }

    @GetMapping("/notes/{id}/images/{imageId}")
    public ResponseEntity<byte[]> extraImage(@PathVariable long id, @PathVariable long imageId) {
        XiaohongshuNoteService.ImageContent image = noteService.extraImage(id, imageId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.mime())).body(image.bytes());
    }

    @PostMapping(path = "/notes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public XiaohongshuNoteResponse save(@RequestParam MultipartFile image,
            @RequestParam(required = false) List<MultipartFile> extraImages,
            @RequestParam(value = "requirements", defaultValue = "") String requirements, @RequestParam String title,
            @RequestParam String body, @RequestParam(defaultValue = "") String tagsText,
            @RequestParam(required = false) Long taskId, @RequestParam(required = false) Integer directionIndex) {
        return noteService.create(image, extraImages, requirements, title, body, tagsText, taskId, directionIndex);
    }

    @PutMapping(path = "/notes/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public XiaohongshuNoteResponse update(@PathVariable long id,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) List<MultipartFile> extraImages,
            @RequestParam(value = "requirements", defaultValue = "") String requirements, @RequestParam String title,
            @RequestParam String body, @RequestParam(defaultValue = "") String tagsText,
            @RequestParam(required = false) Long taskId, @RequestParam(required = false) Integer directionIndex) {
        return noteService.update(id, image, extraImages, requirements, title, body, tagsText, taskId, directionIndex);
    }

    @PostMapping("/notes/{id}/published")
    public XiaohongshuNoteResponse published(@PathVariable long id) {
        return noteService.markPublished(id);
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        noteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
