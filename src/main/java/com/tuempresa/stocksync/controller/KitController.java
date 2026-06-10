package com.tuempresa.stocksync.controller;

import com.tuempresa.stocksync.model.Kit;
import com.tuempresa.stocksync.service.KitService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kits")
@RequiredArgsConstructor
public class KitController {

    private final KitService kitService;

    @GetMapping
    public List<Kit> getAll() {
        return kitService.listarKits();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Kit> create(@RequestBody Kit kit) {
        return ResponseEntity.ok(kitService.crearKit(kit));
    }

    @PostMapping("/{sku}/componentes")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Kit> addComponente(
            @PathVariable @NotBlank String sku,
            @RequestParam @NotBlank String componenteSku,
            @RequestParam @Min(1) int cantidad) {
        return ResponseEntity.ok(kitService.agregarComponente(sku, componenteSku, cantidad));
    }

    @GetMapping("/{sku}/stock")
    public ResponseEntity<Map<String, Object>> getStock(@PathVariable String sku) {
        int stockDisponible = kitService.calcularStockDisponible(sku);
        return ResponseEntity.ok(Map.of(
                "sku", sku,
                "stockDisponible", stockDisponible,
                "esKit", kitService.esKit(sku)
        ));
    }
}
