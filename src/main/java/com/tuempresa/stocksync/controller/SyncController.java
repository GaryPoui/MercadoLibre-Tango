package com.tuempresa.stocksync.controller;

import com.tuempresa.stocksync.model.SyncLog;
import com.tuempresa.stocksync.repository.SyncLogRepository;
import com.tuempresa.stocksync.service.StockSyncService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final StockSyncService stockSyncService;
    private final SyncLogRepository syncLogRepository;

    /**
     * Fuerza sincronización completa desde Sheets → BD + ML + Tango.
     */
    @PostMapping("/from-sheets")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Map<String, Object>> syncFromSheets() {
        int count = stockSyncService.syncFromSheets();
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "productosSync", count,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Fuerza sincronización desde Tango → ML + Sheets.
     */
    @PostMapping("/from-tango")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Map<String, String>> syncFromTango() {
        stockSyncService.syncFromTangoToML();
        return ResponseEntity.ok(Map.of("status", "OK", "timestamp", LocalDateTime.now().toString()));
    }

    /**
     * Fuerza la actualización de un SKU específico en todos los sistemas.
     */
    @PostMapping("/force/{sku}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Map<String, Object>> forceSync(
            @PathVariable String sku,
            @RequestParam @Min(0) int stock) {
        stockSyncService.forceSync(sku, stock);
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "sku", sku,
                "nuevoStock", stock
        ));
    }

    /**
     * Obtiene el historial de sincronizaciones paginado.
     */
    @GetMapping("/logs")
    public Page<SyncLog> getLogs(Pageable pageable) {
        return syncLogRepository.findAll(pageable);
    }

    /**
     * Logs de un SKU específico.
     */
    @GetMapping("/logs/{sku}")
    public Page<SyncLog> getLogsBySku(@PathVariable @NotBlank String sku, Pageable pageable) {
        return syncLogRepository.findBySkuOrderByCreatedAtDesc(sku, pageable);
    }

    /**
     * Resumen de errores de las últimas 24 horas.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        LocalDateTime hace24hs = LocalDateTime.now().minusHours(24);
        long errores = syncLogRepository.countByEstadoAndCreatedAtAfter(SyncLog.SyncStatus.ERROR, hace24hs);
        long exitosos = syncLogRepository.countByEstadoAndCreatedAtAfter(SyncLog.SyncStatus.OK, hace24hs);

        return ResponseEntity.ok(Map.of(
                "status", errores == 0 ? "HEALTHY" : "DEGRADED",
                "ultimas24hs", Map.of(
                        "exitosos", exitosos,
                        "errores", errores
                ),
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
