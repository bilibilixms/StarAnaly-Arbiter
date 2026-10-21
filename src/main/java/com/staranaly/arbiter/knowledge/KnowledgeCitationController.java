package com.staranaly.arbiter.knowledge;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/{taskId}/runs/{runId}/citations")
public class KnowledgeCitationController {

    private final KnowledgeService service;

    public KnowledgeCitationController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping
    public List<KnowledgeCitation> citations(@PathVariable Long taskId, @PathVariable Long runId) {
        return service.citations(taskId, runId);
    }
}
