package com.tuempresa.stocksync.repository;

import com.tuempresa.stocksync.model.SyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    Page<SyncLog> findBySkuOrderByCreatedAtDesc(String sku, Pageable pageable);

    List<SyncLog> findByEstadoAndCreatedAtAfter(SyncLog.SyncStatus estado, LocalDateTime desde);

    long countByEstadoAndCreatedAtAfter(SyncLog.SyncStatus estado, LocalDateTime desde);
}
