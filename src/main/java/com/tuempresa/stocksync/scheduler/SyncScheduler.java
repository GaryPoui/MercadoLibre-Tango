package com.tuempresa.stocksync.scheduler;

import com.tuempresa.stocksync.service.PriceSyncService;
import com.tuempresa.stocksync.service.StockSyncService;
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
}

