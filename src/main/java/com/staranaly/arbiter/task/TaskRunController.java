package com.staranaly.arbiter.task;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/{id}/runs")
public class TaskRunController {

    private final TaskConversationService service;

    public TaskRunController(TaskConversationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TaskRunResponse> start(@PathVariable Long id,
            @Valid @RequestBody SendTaskMessageRequest request) {
        TaskRunResponse run = service.start(id, request.content());
        return ResponseEntity.accepted()
                .location(URI.create("/api/tasks/" + id + "/runs/" + run.id()))
                .body(run);
    }

    @GetMapping
    public PageResponse<TaskRunResponse> list(@PathVariable Long id,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(service.listRuns(id, pageable));
    }

    @GetMapping("/{runId}")
    public TaskRunResponse find(@PathVariable Long id, @PathVariable Long runId) {
        return service.findRun(id, runId);
    }

    @GetMapping("/{runId}/steps")
    public List<TaskRunStepResponse> steps(@PathVariable Long id, @PathVariable Long runId) {
        return service.listSteps(id, runId);
    }
}
