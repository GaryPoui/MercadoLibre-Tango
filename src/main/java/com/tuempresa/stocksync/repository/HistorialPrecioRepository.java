package com.tuempresa.stocksync.repository;

import com.tuempresa.stocksync.model.HistorialPrecio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HistorialPrecioRepository extends JpaRepository<HistorialPrecio, Long> {

    Page<HistorialPrecio> findBySkuOrderByCreatedAtDesc(String sku, Pageable pageable);

    List<HistorialPrecio> findBySkuAndCreatedAtBetweenOrderByCreatedAtAsc(
            String sku, LocalDateTime desde, LocalDateTime hasta);

    List<HistorialPrecio> findTop10ByOrderByCreatedAtDesc();
}
