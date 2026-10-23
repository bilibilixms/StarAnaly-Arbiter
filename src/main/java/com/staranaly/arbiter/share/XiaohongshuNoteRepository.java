package com.staranaly.arbiter.share;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface XiaohongshuNoteRepository extends JpaRepository<XiaohongshuNote, Long> {
    List<XiaohongshuNote> findByStatusOrderByUpdatedAtDescIdDesc(XiaohongshuNote.Status status);
    List<XiaohongshuNote> findByTaskIdOrderByUpdatedAtDescIdDesc(Long taskId);
}
