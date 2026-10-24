package com.staranaly.arbiter.share;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DouyinNoteExtraImageRepository extends JpaRepository<DouyinNoteExtraImage, Long> {
    List<DouyinNoteExtraImage> findByNoteIdOrderByPositionAsc(long noteId);
    void deleteByNoteId(long noteId);
}
