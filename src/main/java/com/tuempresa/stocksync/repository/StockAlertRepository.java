package com.tuempresa.stocksync.repository;

import com.tuempresa.stocksync.model.StockAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    List<StockAlert> findByEstadoOrderByCreatedAtDesc(StockAlert.AlertStatus estado);

    Page<StockAlert> findBySkuOrderByCreatedAtDesc(String sku, Pageable pageable);

    // Evitar duplicar alertas del mismo tipo para el mismo SKU en las últimas N horas
    boolean existsBySkuAndTipoAndCreatedAtAfter(
            String sku, StockAlert.AlertType tipo, LocalDateTime desde);

    long countByEstadoAndCreatedAtAfter(StockAlert.AlertStatus estado, LocalDateTime desde);
}
