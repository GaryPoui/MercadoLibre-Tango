package com.tuempresa.stocksync.service;

import com.tuempresa.stocksync.model.Kit;
import com.tuempresa.stocksync.model.KitComponente;
import com.tuempresa.stocksync.model.StockItem;
import com.tuempresa.stocksync.repository.KitRepository;
import com.tuempresa.stocksync.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class KitService {

    private final KitRepository kitRepository;
    private final StockRepository stockRepository;
    private final MovimientoStockService movimientoService;

    /**
     * Cuando se vende un kit, descuenta stock de cada componente.
     * Se llama desde StockSyncService al detectar una venta de ML con SKU de kit.
     */
    @Transactional
    public void procesarVentaKit(String kitSku, int cantidadVendida, String orderId) {
        Kit kit = kitRepository.findBySku(kitSku)
                .orElseThrow(() -> new IllegalArgumentException("Kit no encontrado: " + kitSku));

        log.info("Procesando venta de kit: sku={} cantidad={} orderId={}", kitSku, cantidadVendida, orderId);

        for (KitComponente componente : kit.getComponentes()) {
            StockItem item = componente.getStockItem();
            int cantADescontar = componente.getCantidad() * cantidadVendida;
            int stockAnterior = item.getStock();
            int nuevoStock = Math.max(0, stockAnterior - cantADescontar);

            item.setStock(nuevoStock);
            stockRepository.save(item);

            movimientoService.registrar(
                    item.getSku(),
                    com.tuempresa.stocksync.model.MovimientoStock.TipoMovimiento.EGRESO_VENTA,
                    cantADescontar, stockAnterior, nuevoStock,
                    null,
                    "Componente de kit " + kitSku,
                    orderId,
                    "SISTEMA-KIT"
            );

            log.info("Componente descontado: sku={} -{} ({}→{})",
                    item.getSku(), cantADescontar, stockAnterior, nuevoStock);
        }
    }

    /**
     * Calcula el stock disponible de un kit (limitado por el componente más escaso).
     */
    public int calcularStockDisponible(String kitSku) {
        return kitRepository.findBySku(kitSku)
                .map(Kit::calcularStockDisponible)
                .orElse(0);
    }

    /**
     * Verifica si un SKU corresponde a un kit activo.
     */
    public boolean esKit(String sku) {
        return kitRepository.findBySku(sku)
                .map(Kit::isActivo)
                .orElse(false);
    }

    @Transactional
    public Kit crearKit(Kit kit) {
        if (kitRepository.existsBySku(kit.getSku())) {
            throw new IllegalArgumentException("Ya existe un kit con SKU: " + kit.getSku());
        }
        return kitRepository.save(kit);
    }

    @Transactional
    public Kit agregarComponente(String kitSku, String componenteSku, int cantidad) {
        Kit kit = kitRepository.findBySku(kitSku)
                .orElseThrow(() -> new IllegalArgumentException("Kit no encontrado: " + kitSku));
        StockItem item = stockRepository.findBySku(componenteSku)
                .orElseThrow(() -> new IllegalArgumentException("SKU componente no encontrado: " + componenteSku));

        KitComponente comp = KitComponente.builder()
                .kit(kit)
                .stockItem(item)
                .cantidad(cantidad)
                .build();

        kit.getComponentes().add(comp);
        return kitRepository.save(kit);
    }

    public List<Kit> listarKits() {
        return kitRepository.findByActivoTrue();
    }
}
