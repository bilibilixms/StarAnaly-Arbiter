package com.staranaly.arbiter.share;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DouyinNoteRepository extends JpaRepository<DouyinNote, Long> {
    List<DouyinNote> findByStatusOrderByUpdatedAtDescIdDesc(DouyinNote.Status status);
    List<DouyinNote> findByTaskIdOrderByUpdatedAtDescIdDesc(Long taskId);
}
