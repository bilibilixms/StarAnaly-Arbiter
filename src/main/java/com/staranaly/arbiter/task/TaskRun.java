package com.staranaly.arbiter.task;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "task_runs")
public class TaskRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Convert(converter = TaskStatusConverter.class)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "request_summary", nullable = false, length = 200)
    private String requestSummary;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    protected TaskRun() {
        // JPA requires a no-argument constructor.
    }

    public TaskRun(Task task, String content) {
        this.task = task;
        this.status = TaskStatus.RUNNING;
        this.requestSummary = "用户消息，" + content.codePointCount(0, content.length()) + " 字符";
        this.startedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return task.getId();
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getRequestSummary() {
        return requestSummary;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void markSucceeded() {
        this.status = TaskStatus.SUCCEEDED;
        this.lastError = null;
        this.finishedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void markFailed(String reason) {
        this.status = TaskStatus.FAILED;
        this.lastError = reason;
        this.finishedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
