package com.staranaly.arbiter.knowledge;

import java.net.URI;
import java.util.List;

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
@RequestMapping("/api/tasks/{taskId}/documents")
public class KnowledgeController {

    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocumentMetadata> upload(@PathVariable Long taskId,
            @RequestParam("file") MultipartFile file) {
        KnowledgeDocumentMetadata saved = service.upload(taskId, file);
        return ResponseEntity.created(URI.create("/api/tasks/" + taskId + "/documents/" + saved.id()))
                .body(saved);
    }

    @GetMapping
    public List<KnowledgeDocumentMetadata> list(@PathVariable Long taskId) {
        return service.list(taskId);
    }

    @GetMapping("/search")
    public KnowledgeSearchResponse search(@PathVariable Long taskId, @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) {
        return service.search(taskId, query, limit);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable Long taskId, @PathVariable Long documentId) {
        service.delete(taskId, documentId);
        return ResponseEntity.noContent().build();
    }
}
