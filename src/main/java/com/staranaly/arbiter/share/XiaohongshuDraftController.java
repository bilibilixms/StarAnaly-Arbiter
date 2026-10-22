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

    public XiaohongshuDraftController(XiaohongshuDraftService service, XiaohongshuNoteService noteService) {
        this.service = service;
        this.noteService = noteService;
    }

    @PostMapping(path = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public XiaohongshuDraftResponse generate(@RequestParam("image") MultipartFile image,
            @RequestParam(value = "requirements", defaultValue = "") String requirements) {
        return service.generate(image, requirements);
    }

    @GetMapping("/notes")
    public List<XiaohongshuNoteResponse> notes(@RequestParam XiaohongshuNote.Status status) {
        return noteService.list(status);
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

    @PostMapping(path = "/notes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public XiaohongshuNoteResponse save(@RequestParam MultipartFile image,
            @RequestParam(value = "requirements", defaultValue = "") String requirements, @RequestParam String title,
            @RequestParam String body, @RequestParam(defaultValue = "") String tagsText) {
        return noteService.create(image, requirements, title, body, tagsText);
    }

    @PutMapping(path = "/notes/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public XiaohongshuNoteResponse update(@PathVariable long id,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(value = "requirements", defaultValue = "") String requirements, @RequestParam String title,
            @RequestParam String body, @RequestParam(defaultValue = "") String tagsText) {
        return noteService.update(id, image, requirements, title, body, tagsText);
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
