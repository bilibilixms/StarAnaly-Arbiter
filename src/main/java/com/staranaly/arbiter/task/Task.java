package com.staranaly.arbiter.task;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 5000)
    private String description;

    @Convert(converter = TaskStatusConverter.class)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_stage", nullable = false, length = 24)
    private TaskWorkflowStage workflowStage;

    @Column(name = "discussion_summary", length = 5000)
    private String discussionSummary;

    protected Task() {
        // JPA requires a no-argument constructor.
    }

    public Task(String title, String description) {
        this.title = title;
        this.description = description;
        this.status = TaskStatus.PENDING;
        this.workflowStage = TaskWorkflowStage.PLANNING;
    }

    @PrePersist
    void setCreationTime() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getLastError() {
        return lastError;
    }

    public TaskWorkflowStage getWorkflowStage() {
        return workflowStage;
    }

    public String getDiscussionSummary() {
        return discussionSummary;
    }

    public void updatePlan(TaskWorkflowStage stage, String summary) {
        this.workflowStage = stage;
        this.discussionSummary = summary == null || summary.isBlank() ? null : summary.trim();
    }

    public void updateRequirements(String nextTitle, String nextDescription) {
        this.title = nextTitle.trim();
        this.description = nextDescription == null || nextDescription.isBlank() ? null : nextDescription.trim();
    }

    public void markRunning() {
        this.status = TaskStatus.RUNNING;
        this.lastError = null;
    }

    public void markSucceeded() {
        this.status = TaskStatus.SUCCEEDED;
        this.lastError = null;
    }

    public void markFailed(String reason) {
        this.status = TaskStatus.FAILED;
        this.lastError = reason;
    }
}
