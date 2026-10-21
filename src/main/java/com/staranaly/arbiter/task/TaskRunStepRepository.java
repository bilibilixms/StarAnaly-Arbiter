package com.staranaly.arbiter.task;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRunStepRepository extends JpaRepository<TaskRunStep, Long> {

    List<TaskRunStep> findByRun_IdOrderByStepNumberAsc(Long runId);

    Optional<TaskRunStep> findByRun_IdAndStepNumber(Long runId, int stepNumber);

    List<TaskRunStep> findByRun_IdAndStatus(Long runId, TaskRunStepStatus status);
}
