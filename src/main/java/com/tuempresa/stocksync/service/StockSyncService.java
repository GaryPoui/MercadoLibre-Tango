package com.tuempresa.stocksync.service;

import com.tuempresa.stocksync.adapter.MLClient;
import com.tuempresa.stocksync.adapter.SheetsClient;
import com.tuempresa.stocksync.adapter.TangoClient;
import com.tuempresa.stocksync.adapter.dto.TangoProductoResponse;
import com.tuempresa.stocksync.model.MovimientoStock;
import com.tuempresa.stocksync.model.StockItem;
import com.tuempresa.stocksync.model.SyncEvent;
import com.tuempresa.stocksync.model.SyncLog;
import com.tuempresa.stocksync.model.SyncPendiente;
import com.tuempresa.stocksync.repository.StockRepository;
import com.tuempresa.stocksync.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockSyncService {

    private final MLClient mlClient;
    private final TangoClient tangoClient;
    private final SheetsClient sheetsClient;
    private final StockRepository stockRepository;
    private final SyncLogRepository syncLogRepository;
    private final KitService kitService;
    private final MovimientoStockService movimientoService;
    private final SyncOutboxService outboxService;

    /**
     * FLUJO 1: Venta en MercadoLibre → descuenta stock en Tango y Sheets.
     * Se ejecuta de forma asíncrona al recibir el webhook de ML.
     */
    @EventListener
    @Async
    @Transactional
    public void onMLSale(SyncEvent event) {
        String sku = event.getSku();
        int soldQty = event.getQuantity();

        log.info("Procesando venta ML: sku={} cantidad={} orderId={}", sku, soldQty, event.getOrderId());

        // ── Si es un Kit, delegar al KitService para descontar componentes ──────
        if (kitService.esKit(sku)) {
            kitService.procesarVentaKit(sku, soldQty, event.getOrderId());
            return;
        }

        StockItem item = stockRepository.findBySku(sku).orElse(null);
        if (item == null) {
            log.warn("SKU no encontrado en BD local: {}. Intentando buscar por mlItemId.", sku);
            item = stockRepository.findByMlItemId(event.getMlItemId()).orElse(null);
        }

        if (item == null) {
            log.error("No se encontró ítem para sku={} mlItemId={}", sku, event.getMlItemId());
            saveLog(sku, SyncLog.SyncOrigin.MERCADOLIBRE, SyncLog.SyncStatus.ERROR,
                    null, null, "SKU no encontrado en la base de datos local");
            return;
        }

        int stockAnterior = item.getStock();
        int nuevoStock = Math.max(0, stockAnterior - soldQty);

        try {
            // Actualizar BD local primero (fuente de verdad)
            item.setStock(nuevoStock);
            item.setUltimaSincronizacion(LocalDateTime.now());
            stockRepository.save(item);

            // Registrar movimiento en el historial
            movimientoService.registrar(sku, MovimientoStock.TipoMovimiento.EGRESO_VENTA,
                    soldQty, stockAnterior, nuevoStock, null,
                    "Venta MercadoLibre", event.getOrderId(), "ML-WEBHOOK");

            // Actualizar Tango y Sheets en paralelo (con outbox en caso de fallo)
            final StockItem finalItem = item;
            final int finalNuevoStock = nuevoStock;

            CompletableFuture<Void> tangoFuture = CompletableFuture.runAsync(() -> {
                try {
                    tangoClient.updateStock(finalItem.getTangoProductoId() != null
                            ? finalItem.getTangoProductoId() : finalItem.getSku(), finalNuevoStock);
                } catch (Exception e) {
                    log.error("Error actualizando Tango para sku={}: {}. Registrando en outbox.", finalItem.getSku(), e.getMessage());
                    outboxService.registrarPendiente(finalItem.getSku(),
                            SyncPendiente.DestinoSync.TANGO,
                            SyncPendiente.TipoOperacion.UPDATE_STOCK,
                            String.valueOf(finalNuevoStock));
                }
            });

            CompletableFuture<Void> sheetsFuture = CompletableFuture.runAsync(() -> {
                try {
                    if (finalItem.getSheetsRow() != null) {
                        sheetsClient.updateStock(finalItem.getSku(), finalNuevoStock, finalItem.getSheetsRow());
                    } else {
                        log.warn("No se tiene número de fila en Sheets para sku={}", finalItem.getSku());
                    }
                } catch (Exception e) {
                    log.error("Error actualizando Sheets para sku={}: {}. Registrando en outbox.", finalItem.getSku(), e.getMessage());
                    outboxService.registrarPendiente(finalItem.getSku(),
                            SyncPendiente.DestinoSync.SHEETS,
                            SyncPendiente.TipoOperacion.UPDATE_STOCK,
                            String.valueOf(finalNuevoStock));
                }
            });

            CompletableFuture.allOf(tangoFuture, sheetsFuture).join();

            saveLog(sku, SyncLog.SyncOrigin.MERCADOLIBRE, SyncLog.SyncStatus.OK,
                    stockAnterior, nuevoStock, null);

            log.info("Sync ML→Tango+Sheets completado: sku={} {} → {}", sku, stockAnterior, nuevoStock);

        } catch (Exception e) {
            log.error("Error en sync de venta ML para sku={}: {}", sku, e.getMessage(), e);
            saveLog(sku, SyncLog.SyncOrigin.MERCADOLIBRE, SyncLog.SyncStatus.ERROR,
                    stockAnterior, nuevoStock, e.getMessage());
        }
    }

    /**
     * FLUJO 2: Tango factura / modifica stock → actualiza ML y Sheets.
     * Invocado por el scheduler periódico.
     */
    @Transactional
    public void syncFromTangoToML() {
        log.debug("Iniciando sync Tango → ML + Sheets");

        List<TangoProductoResponse> productosActualizados;
        try {
            productosActualizados = tangoClient.getProductosActualizados();
        } catch (Exception e) {
            log.error("Error obteniendo productos actualizados desde Tango: {}", e.getMessage());
            return;
        }

        for (TangoProductoResponse producto : productosActualizados) {
            String sku = producto.getCodigo();
            stockRepository.findBySku(sku).ifPresentOrElse(item -> {
                int stockAnterior = item.getStock();
                int nuevoStock = producto.getStockActual();

                if (stockAnterior == nuevoStock) return; // sin cambio

                try {
                    // Actualizar ML
                    if (item.getMlItemId() != null) {
                        mlClient.updateStock(item.getMlItemId(), nuevoStock);
                    }
                    // Actualizar Sheets
                    if (item.getSheetsRow() != null) {
                        sheetsClient.updateStock(sku, nuevoStock, item.getSheetsRow());
                    }

                    item.setStock(nuevoStock);
                    item.setUltimaSincronizacion(LocalDateTime.now());
                    stockRepository.save(item);

                    saveLog(sku, SyncLog.SyncOrigin.TANGO, SyncLog.SyncStatus.OK,
                            stockAnterior, nuevoStock, null);

                    log.info("Sync Tango→ML+Sheets: sku={} {} → {}", sku, stockAnterior, nuevoStock);

                } catch (Exception e) {
                    log.error("Error en sync Tango→ML para sku={}: {}", sku, e.getMessage(), e);
                    saveLog(sku, SyncLog.SyncOrigin.TANGO, SyncLog.SyncStatus.ERROR,
                            stockAnterior, nuevoStock, e.getMessage());
                }
            }, () -> log.warn("Producto Tango con sku={} no existe en BD local", sku));
        }
    }

    /**
     * FLUJO 3: Sincronización inicial / full desde Sheets → BD local + ML + Tango.
     * Se usa para importar el Excel por primera vez o para resincronizar todo.
     */
    @Transactional
    public int syncFromSheets() {
        log.info("Iniciando sincronización completa desde Google Sheets");
        int synced = 0;

        try {
            var allRows = sheetsClient.readAllStock();
            for (var entry : allRows.entrySet()) {
                String sku = entry.getKey();
                SheetsClient.SheetRow row = entry.getValue();

                StockItem item = stockRepository.findBySku(sku).orElseGet(() -> {
                    StockItem newItem = new StockItem();
                    newItem.setSku(sku);
                    return newItem;
                });

                int stockAnterior = item.getStock() != null ? item.getStock() : 0;
                item.setNombre(row.nombre());
                item.setStock(row.stock());
                item.setPrecio(row.precio());
                item.setSheetsRow(row.rowNumber());
                item.setUltimaSincronizacion(LocalDateTime.now());
                stockRepository.save(item);

                // Propagar cambio a ML si tiene ID de publicación
                if (item.getMlItemId() != null && stockAnterior != row.stock()) {
                    try {
                        mlClient.updateStock(item.getMlItemId(), row.stock());
                    } catch (Exception e) {
                        log.warn("No se pudo actualizar ML para sku={}: {}", sku, e.getMessage());
                    }
                }

                synced++;
            }
        } catch (Exception e) {
            log.error("Error en syncFromSheets: {}", e.getMessage(), e);
            throw new RuntimeException("Error sincronizando desde Sheets", e);
        }

        log.info("Sync desde Sheets completado: {} productos procesados", synced);
        return synced;
    }

    /**
     * Fuerza la actualización de stock de un SKU específico en todos los sistemas.
     */
    @Transactional
    public void forceSync(String sku, int nuevoStock) {
        StockItem item = stockRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("SKU no encontrado: " + sku));

        int stockAnterior = item.getStock();

        try {
            if (item.getMlItemId() != null) {
                mlClient.updateStock(item.getMlItemId(), nuevoStock);
            }
            if (item.getTangoProductoId() != null || item.getSku() != null) {
                tangoClient.updateStock(
                        item.getTangoProductoId() != null ? item.getTangoProductoId() : sku,
                        nuevoStock
                );
            }
            if (item.getSheetsRow() != null) {
                sheetsClient.updateStock(sku, nuevoStock, item.getSheetsRow());
            }

            item.setStock(nuevoStock);
            item.setUltimaSincronizacion(LocalDateTime.now());
            stockRepository.save(item);

            saveLog(sku, SyncLog.SyncOrigin.MANUAL, SyncLog.SyncStatus.OK,
                    stockAnterior, nuevoStock, null);

        } catch (Exception e) {
            log.error("Error en forceSync para sku={}: {}", sku, e.getMessage(), e);
            saveLog(sku, SyncLog.SyncOrigin.MANUAL, SyncLog.SyncStatus.ERROR,
                    stockAnterior, nuevoStock, e.getMessage());
            throw new RuntimeException("Error en force sync: " + e.getMessage(), e);
        }
    }

    private void saveLog(String sku, SyncLog.SyncOrigin origen, SyncLog.SyncStatus estado,
                         Integer stockAnterior, Integer stockNuevo, String error) {
        syncLogRepository.save(SyncLog.builder()
                .sku(sku)
                .origen(origen)
                .estado(estado)
                .stockAnterior(stockAnterior)
                .stockNuevo(stockNuevo)
                .mensajeError(error)
                .build());
    }
}
