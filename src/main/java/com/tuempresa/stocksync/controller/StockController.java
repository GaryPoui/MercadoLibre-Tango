package com.tuempresa.stocksync.controller;

import com.tuempresa.stocksync.model.StockItem;
import com.tuempresa.stocksync.repository.StockRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockRepository stockRepository;

    @GetMapping
    public Page<StockItem> getAll(Pageable pageable) {
        return stockRepository.findAll(pageable);
    }

    @GetMapping("/{sku}")
    public ResponseEntity<StockItem> getBySku(@PathVariable String sku) {
        return stockRepository.findBySku(sku)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/bajo-minimo")
    public List<StockItem> getBajoMinimo() {
        return stockRepository.findItemsBajoStockMinimo();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<StockItem> create(@Valid @RequestBody StockItem item) {
        if (stockRepository.existsBySku(item.getSku())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(stockRepository.save(item));
    }

    @PutMapping("/{sku}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<StockItem> update(@PathVariable String sku,
                                             @Valid @RequestBody StockItem item) {
        return stockRepository.findBySku(sku).map(existing -> {
            existing.setNombre(item.getNombre());
            existing.setPrecio(item.getPrecio());
            existing.setStockMinimo(item.getStockMinimo());
            existing.setMlItemId(item.getMlItemId());
            existing.setTangoProductoId(item.getTangoProductoId());
            existing.setSheetsRow(item.getSheetsRow());
            return ResponseEntity.ok(stockRepository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{sku}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String sku) {
        return stockRepository.findBySku(sku).map(item -> {
            stockRepository.delete(item);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    // DTO interno para actualización manual de stock
    public record StockUpdateRequest(@NotBlank String sku, @Min(0) int nuevoStock) {}
}
