package com.staranaly.arbiter.share;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/posters")
public class PosterGenerationController {
    private final PosterGenerationService service;
    public PosterGenerationController(PosterGenerationService service) { this.service = service; }

    @GetMapping("/status")
    public Map<String, Boolean> status() { return Map.of("configured", service.configured()); }

    @PostMapping(path = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<PosterGenerationService.GeneratedPoster> generate(@RequestParam long taskId,
            @RequestParam String direction, @RequestParam(defaultValue = "") String advice,
            @RequestParam int count, @RequestParam(required = false) MultipartFile referenceImage) {
        return service.generate(taskId, direction, advice, count, referenceImage);
    }
}
