package com.staranaly.arbiter.task;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/{id}/messages")
public class TaskConversationController {

    private final TaskConversationService service;

    public TaskConversationController(TaskConversationService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskMessageResponse> list(@PathVariable Long id) {
        return service.list(id);
    }

    @PostMapping
    public TaskMessageResponse send(@PathVariable Long id, @Valid @RequestBody SendTaskMessageRequest request) {
        return service.send(id, request.content());
    }
}
