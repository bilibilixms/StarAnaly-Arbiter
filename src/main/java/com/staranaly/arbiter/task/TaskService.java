package com.staranaly.arbiter.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        Task task = repository.save(new Task(request.title().trim(), request.description()));
        return TaskResponse.from(task);
    }

    @Transactional(readOnly = true)
    public TaskResponse findById(Long id) {
        return repository.findById(id)
                .map(TaskResponse::from)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(TaskResponse::from);
    }
}
