package com.tuempresa.stocksync.service;

import com.tuempresa.stocksync.model.Deposito;
import com.tuempresa.stocksync.model.MovimientoStock;
import com.tuempresa.stocksync.model.StockItem;
import com.tuempresa.stocksync.repository.DepositoRepository;
import com.tuempresa.stocksync.repository.MovimientoStockRepository;
import com.tuempresa.stocksync.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class MovimientoStockService {

    private final MovimientoStockRepository movimientoRepository;
    private final StockRepository stockRepository;
    private final DepositoRepository depositoRepository;

    /**
     * Registra un movimiento y aplica el cambio de stock al StockItem.
     * Es el único punto de entrada para modificar stock manualmente.
     */
    @Transactional
    public MovimientoStock registrarYAplicar(
            String sku,
            MovimientoStock.TipoMovimiento tipo,
            int cantidad,
            Long depositoId,
            String motivo,
            String referenciaExterna) {

        StockItem item = stockRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("SKU no encontrado: " + sku));

        int stockAntes = item.getStock();
        int stockDespues = calcularNuevoStock(stockAntes, tipo, cantidad);

        item.setStock(stockDespues);
        stockRepository.save(item);

        Deposito deposito = depositoId != null
                ? depositoRepository.findById(depositoId).orElse(null)
                : null;

        MovimientoStock mov = registrar(sku, tipo, cantidad, stockAntes, stockDespues,
                deposito, motivo, referenciaExterna, "USUARIO");

        log.info("Movimiento registrado: sku={} tipo={} cantidad={} {}→{}",
                sku, tipo, cantidad, stockAntes, stockDespues);

        return mov;
    }

    /**
     * Registra el movimiento en el historial sin modificar el stock (usado internamente).
     */
    public MovimientoStock registrar(
            String sku,
            MovimientoStock.TipoMovimiento tipo,
            int cantidad,
            int stockAntes,
            int stockDespues,
            Deposito deposito,
            String motivo,
            String referenciaExterna,
            String origenSistema) {

        return movimientoRepository.save(MovimientoStock.builder()
                .sku(sku)
                .tipo(tipo)
                .cantidad(cantidad)
                .stockAntes(stockAntes)
                .stockDespues(stockDespues)
                .deposito(deposito)
                .motivo(motivo)
                .referenciaExterna(referenciaExterna)
                .origenSistema(origenSistema)
                .fechaMovimiento(LocalDateTime.now())
                .build());
    }

    public Page<MovimientoStock> getMovimientosPorSku(String sku, Pageable pageable) {
        return movimientoRepository.findBySkuOrderByFechaMovimientoDesc(sku, pageable);
    }

    public Page<MovimientoStock> getAllMovimientos(Pageable pageable) {
        return movimientoRepository.findAll(pageable);
    }

    private int calcularNuevoStock(int stockActual, MovimientoStock.TipoMovimiento tipo, int cantidad) {
        return switch (tipo) {
            case INGRESO_COMPRA, INGRESO_DEVOLUCION,
                 INGRESO_TRANSFERENCIA, AJUSTE_POSITIVO -> stockActual + cantidad;
            case EGRESO_VENTA, EGRESO_MERMA,
                 EGRESO_TRANSFERENCIA, AJUSTE_NEGATIVO  -> Math.max(0, stockActual - cantidad);
        };
    }
}
