package com.tuempresa.stocksync.repository;

import com.tuempresa.stocksync.model.StockPorDeposito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockPorDepositoRepository extends JpaRepository<StockPorDeposito, Long> {
    List<StockPorDeposito> findByStockItem_Sku(String sku);
    Optional<StockPorDeposito> findByStockItem_SkuAndDeposito_Id(String sku, Long depositoId);

    @Query("SELECT SUM(s.stock) FROM StockPorDeposito s WHERE s.stockItem.sku = :sku AND s.deposito.exportaAML = true AND s.deposito.activo = true")
    Integer sumStockMLBysku(String sku);

    @Query("SELECT SUM(s.stock) FROM StockPorDeposito s WHERE s.stockItem.sku = :sku")
    Integer sumStockTotalBySku(String sku);
}
