package com.staranaly.arbiter.share;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface XiaohongshuMaterialRepository extends JpaRepository<XiaohongshuMaterial, Long> {
    List<XiaohongshuMaterial> findAllByOrderByUpdatedAtDescIdDesc();
}
