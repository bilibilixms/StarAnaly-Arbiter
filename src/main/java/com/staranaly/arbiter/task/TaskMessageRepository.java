package com.staranaly.arbiter.task;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskMessageRepository extends JpaRepository<TaskMessage, Long> {

    List<TaskMessage> findByTaskIdOrderByIdAsc(Long taskId);

    List<TaskMessage> findTop20ByTaskIdOrderByIdDesc(Long taskId);
}
