package com.staranaly.arbiter.task;

import java.net.URI;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tasks/{taskId}/images")
public class TaskImageController {
    private final TaskImageService service;

    public TaskImageController(TaskImageService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskImageResponse> list(@PathVariable Long taskId) {
        return service.list(taskId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TaskImageResponse> upload(@PathVariable Long taskId, @RequestParam MultipartFile file) {
        TaskImageResponse saved = service.upload(taskId, file);
        return ResponseEntity.created(URI.create("/api/tasks/" + taskId + "/images/" + saved.id())).body(saved);
    }

    @GetMapping("/{imageId}")
    public ResponseEntity<byte[]> content(@PathVariable Long taskId, @PathVariable Long imageId) {
        TaskImageService.ImageContent image = service.content(taskId, imageId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.mime())).body(image.bytes());
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long taskId, @PathVariable Long imageId) {
        service.delete(taskId, imageId);
        return ResponseEntity.noContent().build();
    }
}
