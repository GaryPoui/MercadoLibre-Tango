package com.tuempresa.stocksync.scheduler;

import com.tuempresa.stocksync.service.PriceSyncService;
import com.tuempresa.stocksync.service.StockSyncService;
import com.tuempresa.stocksync.service.SyncOutboxService;
import com.tuempresa.stocksync.service.VarianteSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SyncScheduler {

    private final StockSyncService syncService;
    private final PriceSyncService priceSyncService;
    private final SyncOutboxService outboxService;
    private final VarianteSyncService varianteSyncService;

    /**
     * Polling periódico Tango → ML + Sheets (stock).
     */
    @Scheduled(fixedDelayString = "${sync.scheduler.tango-poll-interval-ms:300000}")
    public void pollTango() {
        log.debug("Scheduler: iniciando poll de Tango");
        try {
            syncService.syncFromTangoToML();
        } catch (Exception e) {
            log.error("Error en scheduler pollTango: {}", e.getMessage(), e);
        }
    }

    /**
     * Polling periódico Google Sheets → BD local (stock + precios).
     */
    @Scheduled(fixedDelayString = "${sync.scheduler.sheets-poll-interval-ms:600000}")
    public void pollSheets() {
        log.debug("Scheduler: iniciando poll de Google Sheets");
        try {
            syncService.syncFromSheets();
        } catch (Exception e) {
            log.error("Error en scheduler pollSheets: {}", e.getMessage(), e);
        }
    }

    /**
     * Sincronización de precios: Sheets → ML + Tango.
     * Por defecto cada 15 minutos.
     */
    @Scheduled(fixedDelayString = "${sync.scheduler.price-poll-interval-ms:900000}")
    public void pollPrecios() {
        log.debug("Scheduler: iniciando sync de precios");
        try {
            int actualizados = priceSyncService.sincronizarPrecios();
            if (actualizados > 0) {
                log.info("Scheduler: {} precios actualizados", actualizados);
            }
        } catch (Exception e) {
            log.error("Error en scheduler pollPrecios: {}", e.getMessage(), e);
        }
    }

    /**
     * Reintento de operaciones fallidas (outbox).
     * Cada 60 segundos revisa si hay syncs pendientes de reintento.
     */
    @Scheduled(fixedDelayString = "${sync.scheduler.outbox-retry-interval-ms:60000}")
    public void retryOutbox() {
        try {
            int procesados = outboxService.procesarPendientes();
            if (procesados > 0) {
                log.info("Scheduler outbox: {} operaciones reintentadas con éxito", procesados);
            }
        } catch (Exception e) {
            log.error("Error en scheduler retryOutbox: {}", e.getMessage(), e);
        }
    }

    /**
     * Sincronización de variantes (stock + precio) hacia ML.
     * Por defecto cada 10 minutos.
     */
    @Scheduled(fixedDelayString = "${sync.scheduler.variantes-poll-interval-ms:600000}")
    public void pollVariantes() {
        log.debug("Scheduler: iniciando sync de variantes");
        try {
            int stockSync = varianteSyncService.syncVariantesStock();
            int precioSync = varianteSyncService.syncVariantesPrecios();
            if (stockSync > 0 || precioSync > 0) {
                log.info("Scheduler variantes: {} stock + {} precios sincronizados", stockSync, precioSync);
            }
        } catch (Exception e) {
            log.error("Error en scheduler pollVariantes: {}", e.getMessage(), e);
        }
    }
}

