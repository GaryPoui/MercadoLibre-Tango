package com.tuempresa.stocksync.controller;

import com.tuempresa.stocksync.model.MovimientoStock;
import com.tuempresa.stocksync.service.MovimientoStockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private final MovimientoStockService movimientoService;

    /**
     * Registra un ingreso o egreso manual de mercadería.
     * Este es el endpoint principal para operaciones de almacén.
     */
    @PostMapping
    public ResponseEntity<MovimientoStock> registrar(@Valid @RequestBody MovimientoRequest req) {
        MovimientoStock mov = movimientoService.registrarYAplicar(
                req.sku(),
                req.tipo(),
                req.cantidad(),
                req.depositoId(),
                req.motivo(),
                req.referenciaExterna()
        );
        return ResponseEntity.ok(mov);
    }

    @GetMapping
    public Page<MovimientoStock> getAll(Pageable pageable) {
        return movimientoService.getAllMovimientos(pageable);
    }

    @GetMapping("/{sku}")
    public Page<MovimientoStock> getBySku(@PathVariable @NotBlank String sku, Pageable pageable) {
        return movimientoService.getMovimientosPorSku(sku, pageable);
    }

    // DTO interno
    public record MovimientoRequest(
            @NotBlank String sku,
            @NotNull MovimientoStock.TipoMovimiento tipo,
            @Min(1) int cantidad,
            Long depositoId,       // opcional
            String motivo,
            String referenciaExterna  // nro de remito, orden, factura, etc.
    ) {}
}
