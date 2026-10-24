package com.staranaly.arbiter.share;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DouyinMaterialRepository extends JpaRepository<DouyinMaterial, Long> {
    List<DouyinMaterial> findAllByOrderByUpdatedAtDescIdDesc();
}
