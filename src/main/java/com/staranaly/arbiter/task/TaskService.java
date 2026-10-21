package com.staranaly.arbiter.task;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    public Page<TaskResponse> findAll(Pageable pageable, TaskStatus status, String query) {
        String term = query == null ? "" : TaskTitleMatcher.normalize(query);
        if (term.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "搜索词不能超过 200 字符。");
        }
        if (term.isEmpty()) {
            return (status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable))
                    .map(TaskResponse::from);
        }

        List<TaskSearchCandidate> candidates = status == null
                ? repository.findSearchCandidates() : repository.findSearchCandidatesByStatus(status);
        int maxDistance = TaskTitleMatcher.maxDistance(term);
        List<Long> matchingIds = candidates.stream()
                .map(candidate -> new Match(candidate.getId(), TaskTitleMatcher.distance(candidate.getTitle(), term)))
                .filter(match -> match.distance() <= maxDistance)
                .sorted(Comparator.comparingInt(Match::distance).thenComparing(Match::id, Comparator.reverseOrder()))
                .map(Match::id)
                .toList();
        int from = (int) Math.min(pageable.getOffset(), matchingIds.size());
        int to = (int) Math.min((long) from + pageable.getPageSize(), matchingIds.size());
        List<Long> pageIds = matchingIds.subList(from, to);
        Map<Long, Task> tasks = repository.findAllById(pageIds).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
        List<TaskResponse> content = pageIds.stream().map(tasks::get).filter(java.util.Objects::nonNull)
                .map(TaskResponse::from).toList();
        return new PageImpl<>(content, pageable, matchingIds.size());
    }

    private record Match(Long id, int distance) {
    }
}
