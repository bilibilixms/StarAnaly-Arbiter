package com.staranaly.arbiter.share;

import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/posters")
public class PosterGenerationController {
    private final PosterGenerationService service;
    private final PosterMaterialService materials;
    private final PosterJobService jobs;
    public PosterGenerationController(PosterGenerationService service, PosterMaterialService materials, PosterJobService jobs) {
        this.service = service;
        this.materials = materials;
        this.jobs = jobs;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        PosterJobService.Quota quota = jobs.quota();
        return Map.of("configured", service.configured(), "dailyLimit", quota.dailyLimit(), "usedToday", quota.usedToday());
    }

    @GetMapping("/materials")
    public List<PosterMaterialResponse> materials() { return materials.list(); }

    @PostMapping(path = "/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PosterMaterialResponse createMaterial(@RequestParam String name, @RequestParam PosterMaterial.Type type,
            @RequestParam(required = false) String content, @RequestParam(required = false) MultipartFile file) {
        return materials.create(name, type, content, file);
    }

    @PutMapping(path = "/materials/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PosterMaterialResponse updateMaterial(@PathVariable long id, @RequestParam String name,
            @RequestParam(required = false) String content, @RequestParam(required = false) MultipartFile file) {
        return materials.update(id, name, content, file);
    }

    @DeleteMapping("/materials/{id}")
    public ResponseEntity<Void> deleteMaterial(@PathVariable long id) {
        materials.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/materials/{id}/image")
    public ResponseEntity<byte[]> materialImage(@PathVariable long id) {
        PosterMaterial material = materials.image(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(material.getMime())).body(material.getImageData());
    }

    @GetMapping("/tasks/{taskId}/jobs")
    public List<PosterJobService.Job> jobs(@PathVariable long taskId) { return jobs.list(taskId); }

    @GetMapping("/tasks/{taskId}/jobs/{jobId}")
    public PosterJobService.Job job(@PathVariable long taskId, @PathVariable long jobId) { return jobs.get(taskId, jobId); }

    @GetMapping("/tasks/{taskId}/jobs/{jobId}/images/{imageId}")
    public ResponseEntity<byte[]> image(@PathVariable long taskId, @PathVariable long jobId, @PathVariable long imageId) {
        PosterJobService.ImageData image = jobs.image(taskId, jobId, imageId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.mime())).body(image.bytes());
    }

    @GetMapping("/tasks/{taskId}/jobs/{jobId}/logos/{materialId}")
    public ResponseEntity<byte[]> logo(@PathVariable long taskId, @PathVariable long jobId, @PathVariable long materialId) {
        PosterJobService.ImageData logo = jobs.logo(taskId, jobId, materialId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(logo.mime())).body(logo.bytes());
    }

    @PostMapping(path = "/tasks/{taskId}/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PosterJobService.Job submit(@PathVariable long taskId, @RequestParam String requestKey,
            @RequestParam String direction, @RequestParam(defaultValue = "") String advice, @RequestParam int count,
            @RequestParam(defaultValue = "") String requiredText, @RequestParam(defaultValue = "") String forbiddenContent,
            @RequestParam(required = false) MultipartFile referenceImage,
            @RequestParam(required = false) List<Long> materialIds) {
        return jobs.submit(taskId, requestKey, direction, advice, count, referenceImage,
                materials.selected(materialIds), requiredText, forbiddenContent);
    }

    @PostMapping(path = "/tasks/{taskId}/jobs/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PosterJobService.Job importBatch(@PathVariable long taskId, @RequestParam String requestKey,
            @RequestParam String direction, @RequestParam(defaultValue = "") String advice,
            @RequestParam(defaultValue = "") String requiredText, @RequestParam(defaultValue = "") String forbiddenContent,
            @RequestParam List<MultipartFile> files, @RequestParam(required = false) List<Long> materialIds) {
        return jobs.importBatch(taskId, requestKey, direction, advice, files,
                materials.selected(materialIds), requiredText, forbiddenContent);
    }

    @PutMapping("/tasks/{taskId}/jobs/{jobId}/selection")
    public ResponseEntity<Void> selection(@PathVariable long taskId, @PathVariable long jobId,
            @RequestBody List<Integer> positions) {
        jobs.select(taskId, jobId, positions);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/tasks/{taskId}/jobs/{jobId}/images/{imageId}/review")
    public ResponseEntity<Void> review(@PathVariable long taskId, @PathVariable long jobId, @PathVariable long imageId,
            @RequestBody PosterJobService.Review review) {
        jobs.review(taskId, jobId, imageId, review);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(path = "/tasks/{taskId}/jobs/{jobId}/images/{imageId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> replaceImage(@PathVariable long taskId, @PathVariable long jobId,
            @PathVariable long imageId, @RequestParam MultipartFile file) {
        jobs.replaceImage(taskId, jobId, imageId, file);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks/{taskId}/jobs/{jobId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable long taskId, @PathVariable long jobId) {
        jobs.cancel(taskId, jobId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/tasks/{taskId}/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(@PathVariable long taskId, @PathVariable long jobId) {
        jobs.delete(taskId, jobId);
        return ResponseEntity.noContent().build();
    }
}
