package com.tuempresa.stocksync.repository;

import com.tuempresa.stocksync.model.MovimientoStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {
    Page<MovimientoStock> findBySkuOrderByFechaMovimientoDesc(String sku, Pageable pageable);
    Page<MovimientoStock> findByTipoOrderByFechaMovimientoDesc(MovimientoStock.TipoMovimiento tipo, Pageable pageable);

    List<MovimientoStock> findBySkuAndFechaMovimientoBetweenOrderByFechaMovimientoAsc(
            String sku, LocalDateTime desde, LocalDateTime hasta);

    @Query("SELECT SUM(CASE WHEN m.tipo IN ('INGRESO_COMPRA','INGRESO_DEVOLUCION','INGRESO_TRANSFERENCIA','AJUSTE_POSITIVO') THEN m.cantidad ELSE 0 END) " +
           "- SUM(CASE WHEN m.tipo IN ('EGRESO_VENTA','EGRESO_MERMA','EGRESO_TRANSFERENCIA','AJUSTE_NEGATIVO') THEN m.cantidad ELSE 0 END) " +
           "FROM MovimientoStock m WHERE m.sku = :sku AND m.fechaMovimiento >= :desde")
    Integer calcularMovimientoNeto(String sku, LocalDateTime desde);
}
