package com.staranaly.arbiter.task;

import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse task = service.create(request);
        return ResponseEntity.created(URI.create("/api/tasks/" + task.id())).body(task);
    }

    @GetMapping("/{id}")
    public TaskResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping
    public PageResponse<TaskResponse> findAll(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(service.findAll(pageable));
    }
}
