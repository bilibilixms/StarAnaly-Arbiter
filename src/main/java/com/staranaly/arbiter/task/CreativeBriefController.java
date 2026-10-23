package com.staranaly.arbiter.task;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/{taskId}/brief")
public class CreativeBriefController {
    private final CreativeBriefService service;

    public CreativeBriefController(CreativeBriefService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<CreativeBriefResponse> get(@PathVariable Long taskId) {
        return service.get(taskId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/generate")
    public CreativeBriefResponse generate(@PathVariable Long taskId) {
        return service.generate(taskId);
    }

    @PutMapping
    public CreativeBriefResponse save(@PathVariable Long taskId, @RequestBody SaveCreativeBriefRequest request) {
        return service.save(taskId, request);
    }
}
