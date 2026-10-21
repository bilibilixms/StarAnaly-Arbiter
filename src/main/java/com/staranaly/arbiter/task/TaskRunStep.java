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
@Table(name = "task_run_steps")
public class TaskRunStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private TaskRun run;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Convert(converter = TaskRunStepKindConverter.class)
    @Column(nullable = false, length = 30)
    private TaskRunStepKind kind;

    @Convert(converter = TaskRunStepStatusConverter.class)
    @Column(nullable = false, length = 20)
    private TaskRunStepStatus status;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "input_summary", nullable = false, length = 500)
    private String inputSummary;

    @Column(name = "output_summary", length = 500)
    private String outputSummary;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    protected TaskRunStep() {
        // JPA requires a no-argument constructor.
    }

    public TaskRunStep(TaskRun run, int stepNumber, TaskRunStepKind kind, String inputSummary) {
        this.run = run;
        this.stepNumber = stepNumber;
        this.kind = kind;
        this.status = TaskRunStepStatus.RUNNING;
        this.inputSummary = inputSummary;
        this.startedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public TaskRunStepKind getKind() {
        return kind;
    }

    public TaskRunStepStatus getStatus() {
        return status;
    }

    public String getToolName() {
        return toolName;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void markSucceeded(String summary) {
        this.status = TaskRunStepStatus.SUCCEEDED;
        this.outputSummary = summary;
        this.finishedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void markFailed(String reason) {
        this.status = TaskRunStepStatus.FAILED;
        this.outputSummary = reason;
        this.finishedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
