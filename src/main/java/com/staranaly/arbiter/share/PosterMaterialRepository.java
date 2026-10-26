package com.staranaly.arbiter.share;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PosterMaterialRepository extends JpaRepository<PosterMaterial, Long> {
    @Query("select new com.staranaly.arbiter.share.PosterMaterialResponse("
            + "m.id, m.name, m.type, m.content, m.sourceName, m.mime, m.updatedAt) "
            + "from PosterMaterial m order by m.updatedAt desc, m.id desc")
    List<PosterMaterialResponse> listSummaries();
}
