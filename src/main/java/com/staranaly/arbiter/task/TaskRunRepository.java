package com.staranaly.arbiter.task;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRunRepository extends JpaRepository<TaskRun, Long> {

    Page<TaskRun> findByTask_Id(Long taskId, Pageable pageable);

    Optional<TaskRun> findByIdAndTask_Id(Long id, Long taskId);

    List<TaskRun> findByStatus(TaskStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from TaskRun run where run.id = :id")
    Optional<TaskRun> findByIdForUpdate(@Param("id") Long id);
}
