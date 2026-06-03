package com.tuempresa.stocksync.service;

import com.tuempresa.stocksync.adapter.MLClient;
import com.tuempresa.stocksync.adapter.SheetsClient;
import com.tuempresa.stocksync.adapter.TangoClient;
import com.tuempresa.stocksync.model.StockItem;
import com.tuempresa.stocksync.model.SyncPendiente;
import com.tuempresa.stocksync.model.SyncPendiente.DestinoSync;
import com.tuempresa.stocksync.model.SyncPendiente.EstadoSync;
import com.tuempresa.stocksync.model.SyncPendiente.TipoOperacion;
import com.tuempresa.stocksync.repository.StockRepository;
import com.tuempresa.stocksync.repository.SyncPendienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio de outbox: registra syncs pendientes y procesa reintentos.
 * Garantiza que si un destino externo falla, la operación se reintenta automáticamente
 * con backoff exponencial hasta un máximo de intentos.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SyncOutboxService {

    private final SyncPendienteRepository syncPendienteRepository;
    private final MLClient mlClient;
    private final TangoClient tangoClient;
    private final SheetsClient sheetsClient;
    private final StockRepository stockRepository;

    /**
     * Registra una operación pendiente en el outbox.
     */
    public void registrarPendiente(String sku, DestinoSync destino,
                                   TipoOperacion tipo, String payload) {
        SyncPendiente pendiente = SyncPendiente.builder()
                .sku(sku)
                .destino(destino)
                .tipoOperacion(tipo)
                .payload(payload)
                .estado(EstadoSync.PENDIENTE)
                .build();
        syncPendienteRepository.save(pendiente);
        log.info("Outbox: registrada operación pendiente sku={} destino={} tipo={}", sku, destino, tipo);
    }

    /**
     * Procesa todas las operaciones pendientes cuyo tiempo de reintento ya pasó.
     * Llamado por el scheduler cada 60 segundos.
     */
    @Transactional
    public int procesarPendientes() {
        List<SyncPendiente> pendientes = syncPendienteRepository
                .findPendientesListos(EstadoSync.PENDIENTE, LocalDateTime.now());

        int procesados = 0;
        for (SyncPendiente sp : pendientes) {
            sp.setEstado(EstadoSync.EN_PROCESO);
            syncPendienteRepository.save(sp);

            try {
                ejecutarSync(sp);
                sp.setEstado(EstadoSync.COMPLETADO);
                sp.setUltimoError(null);
                syncPendienteRepository.save(sp);
                procesados++;
                log.info("Outbox: operación completada id={} sku={} destino={}",
                        sp.getId(), sp.getSku(), sp.getDestino());
            } catch (Exception e) {
                manejarFallo(sp, e);
            }
        }

        if (procesados > 0) {
            log.info("Outbox: {} operaciones procesadas exitosamente", procesados);
        }
        return procesados;
    }

    private void ejecutarSync(SyncPendiente sp) {
        StockItem item = stockRepository.findBySku(sp.getSku()).orElse(null);

        switch (sp.getTipoOperacion()) {
            case UPDATE_STOCK -> ejecutarUpdateStock(sp, item);
            case UPDATE_PRICE -> ejecutarUpdatePrice(sp, item);
        }
    }

    private void ejecutarUpdateStock(SyncPendiente sp, StockItem item) {
        int nuevoStock = Integer.parseInt(sp.getPayload());
        switch (sp.getDestino()) {
            case MERCADOLIBRE -> {
                if (item != null && item.getMlItemId() != null) {
                    mlClient.updateStock(item.getMlItemId(), nuevoStock);
                }
            }
            case TANGO -> {
                String tangoId = (item != null && item.getTangoProductoId() != null)
                        ? item.getTangoProductoId() : sp.getSku();
                tangoClient.updateStock(tangoId, nuevoStock);
            }
            case SHEETS -> {
                if (item != null && item.getSheetsRow() != null) {
                    sheetsClient.updateStock(sp.getSku(), nuevoStock, item.getSheetsRow());
                }
            }
        }
    }

    private void ejecutarUpdatePrice(SyncPendiente sp, StockItem item) {
        BigDecimal nuevoPrecio = new BigDecimal(sp.getPayload());
        switch (sp.getDestino()) {
            case MERCADOLIBRE -> {
                if (item != null && item.getMlItemId() != null) {
                    mlClient.updatePrice(item.getMlItemId(), nuevoPrecio);
                }
            }
            case TANGO -> {
                String tangoId = (item != null && item.getTangoProductoId() != null)
                        ? item.getTangoProductoId() : sp.getSku();
                tangoClient.updatePrice(tangoId, nuevoPrecio);
            }
            case SHEETS -> {
                // Precio en sheets se actualiza via la columna correspondiente
                log.debug("Outbox: precio en Sheets no requiere update (fuente de verdad)");
            }
        }
    }

    private void manejarFallo(SyncPendiente sp, Exception e) {
        sp.setIntentos(sp.getIntentos() + 1);
        sp.setUltimoError(e.getMessage());

        if (sp.getIntentos() >= sp.getMaxIntentos()) {
            sp.setEstado(EstadoSync.FALLIDO_PERMANENTE);
            log.error("Outbox: operación FALLIDA PERMANENTE id={} sku={} destino={} tras {} intentos",
                    sp.getId(), sp.getSku(), sp.getDestino(), sp.getIntentos());
        } else {
            // Backoff exponencial: 1min, 2min, 4min, 8min, 16min
            long delayMinutes = (long) Math.pow(2, sp.getIntentos() - 1);
            sp.setProximoIntento(LocalDateTime.now().plusMinutes(delayMinutes));
            sp.setEstado(EstadoSync.PENDIENTE);
            log.warn("Outbox: reintento programado id={} sku={} destino={} intento={}/{} proximo={}",
                    sp.getId(), sp.getSku(), sp.getDestino(),
                    sp.getIntentos(), sp.getMaxIntentos(), sp.getProximoIntento());
        }
        syncPendienteRepository.save(sp);
    }

    public long countPendientes() {
        return syncPendienteRepository.countByEstado(EstadoSync.PENDIENTE);
    }
}
