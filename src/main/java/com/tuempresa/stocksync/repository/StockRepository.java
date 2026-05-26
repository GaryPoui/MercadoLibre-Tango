package com.tuempresa.stocksync.repository;

import com.tuempresa.stocksync.model.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<StockItem, Long> {

    Optional<StockItem> findBySku(String sku);

    Optional<StockItem> findByMlItemId(String mlItemId);

    Optional<StockItem> findByTangoProductoId(String tangoProductoId);

    @Query("SELECT s FROM StockItem s WHERE s.stock <= s.stockMinimo AND s.stockMinimo IS NOT NULL")
    List<StockItem> findItemsBajoStockMinimo();

    List<StockItem> findByMlItemIdIsNotNull();

    boolean existsBySku(String sku);
}
