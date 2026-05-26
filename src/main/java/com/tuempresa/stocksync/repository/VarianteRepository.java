package com.tuempresa.stocksync.repository;

import com.tuempresa.stocksync.model.Variante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VarianteRepository extends JpaRepository<Variante, Long> {
    Optional<Variante> findBySku(String sku);
    Optional<Variante> findByMlVarianteId(String mlVarianteId);
    List<Variante> findByStockItemPadre_Sku(String skuPadre);
    List<Variante> findByStockItemPadre_SkuAndActivoTrue(String skuPadre);
}
