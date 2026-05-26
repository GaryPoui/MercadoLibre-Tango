package com.tuempresa.stocksync.controller;

import com.tuempresa.stocksync.model.Deposito;
import com.tuempresa.stocksync.model.StockPorDeposito;
import com.tuempresa.stocksync.repository.DepositoRepository;
import com.tuempresa.stocksync.service.DepositoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/depositos")
@RequiredArgsConstructor
public class DepositoController {

    private final DepositoRepository depositoRepository;
    private final DepositoService depositoService;

    @GetMapping
    public List<Deposito> getAll() {
        return depositoRepository.findByActivoTrue();
    }

    @PostMapping
    public ResponseEntity<Deposito> create(@Valid @RequestBody Deposito deposito) {
        return ResponseEntity.ok(depositoRepository.save(deposito));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Deposito> update(@PathVariable Long id, @RequestBody Deposito deposito) {
        return depositoRepository.findById(id).map(d -> {
            d.setNombre(deposito.getNombre());
            d.setDireccion(deposito.getDireccion());
            d.setExportaAML(deposito.isExportaAML());
            d.setActivo(deposito.isActivo());
            d.setTangoDepositoId(deposito.getTangoDepositoId());
            return ResponseEntity.ok(depositoRepository.save(d));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ─── Stock por depósito ────────────────────────────────────────────────────

    @GetMapping("/{id}/stock")
    public ResponseEntity<Map<String, Integer>> getStock(@PathVariable Long id) {
        return ResponseEntity.ok(depositoService.getResumenPorDeposito(id));
    }

    @GetMapping("/stock/{sku}")
    public List<StockPorDeposito> getStockBySku(@PathVariable String sku) {
        return depositoService.getDistribucionStock(sku);
    }

    @GetMapping("/stock/{sku}/total")
    public ResponseEntity<Map<String, Integer>> getStockTotal(@PathVariable String sku) {
        return ResponseEntity.ok(Map.of(
                "total", depositoService.getStockTotal(sku),
                "disponibleML", depositoService.getStockParaML(sku)
        ));
    }

    @PutMapping("/{depositoId}/stock/{sku}")
    public ResponseEntity<StockPorDeposito> updateStock(
            @PathVariable Long depositoId,
            @PathVariable String sku,
            @RequestParam int stock) {
        return ResponseEntity.ok(depositoService.actualizarStockEnDeposito(sku, depositoId, stock));
    }
}
