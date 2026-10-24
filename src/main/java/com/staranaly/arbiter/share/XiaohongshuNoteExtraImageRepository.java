package com.staranaly.arbiter.share;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface XiaohongshuNoteExtraImageRepository extends JpaRepository<XiaohongshuNoteExtraImage, Long> {
    List<XiaohongshuNoteExtraImage> findByNoteIdOrderByPositionAsc(long noteId);
    void deleteByNoteId(long noteId);
}
