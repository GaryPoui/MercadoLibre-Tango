package com.tuempresa.stocksync.repository;

import com.tuempresa.stocksync.model.Kit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface KitRepository extends JpaRepository<Kit, Long> {
    Optional<Kit> findBySku(String sku);
    Optional<Kit> findByMlItemId(String mlItemId);
    List<Kit> findByActivoTrue();
    boolean existsBySku(String sku);
}
