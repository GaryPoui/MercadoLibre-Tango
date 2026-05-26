package com.tuempresa.stocksync.controller;

import com.tuempresa.stocksync.model.Variante;
import com.tuempresa.stocksync.model.StockItem;
import com.tuempresa.stocksync.repository.VarianteRepository;
import com.tuempresa.stocksync.repository.StockRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/variantes")
@RequiredArgsConstructor
public class VarianteController {

    private final VarianteRepository varianteRepository;
    private final StockRepository stockRepository;

    @GetMapping("/producto/{skuPadre}")
    public List<Variante> getByProducto(@PathVariable String skuPadre) {
        return varianteRepository.findByStockItemPadre_SkuAndActivoTrue(skuPadre);
    }

    @GetMapping("/{sku}")
    public ResponseEntity<Variante> getBySku(@PathVariable String sku) {
        return varianteRepository.findBySku(sku)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Variante> create(@Valid @RequestBody Variante variante) {
        StockItem padre = stockRepository.findBySku(variante.getStockItemPadre().getSku())
                .orElseThrow(() -> new IllegalArgumentException(
                        "SKU padre no encontrado: " + variante.getStockItemPadre().getSku()));
        variante.setStockItemPadre(padre);
        return ResponseEntity.ok(varianteRepository.save(variante));
    }

    @PutMapping("/{id}/stock")
    public ResponseEntity<Variante> updateStock(@PathVariable Long id, @RequestParam int stock) {
        return varianteRepository.findById(id).map(v -> {
            v.setStock(stock);
            return ResponseEntity.ok(varianteRepository.save(v));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        return varianteRepository.findById(id).map(v -> {
            v.setActivo(false);
            varianteRepository.save(v);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
