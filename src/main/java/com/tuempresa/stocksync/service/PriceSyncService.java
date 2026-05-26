package com.tuempresa.stocksync.service;

import com.tuempresa.stocksync.adapter.MLClient;
import com.tuempresa.stocksync.adapter.SheetsClient;
import com.tuempresa.stocksync.adapter.TangoClient;
import com.tuempresa.stocksync.model.StockItem;
import com.tuempresa.stocksync.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriceSyncService {

    private final SheetsClient sheetsClient;
    private final MLClient mlClient;
    private final TangoClient tangoClient;
    private final StockRepository stockRepository;

    private static final BigDecimal TOLERANCIA_PORCENTUAL = new BigDecimal("0.01"); // 1%

    /**
     * Detecta cambios de precio en Google Sheets y los propaga a ML y Tango.
     * Se ejecuta periódicamente desde el scheduler.
     *
     * @return Cantidad de productos con precio actualizado
     */
    @Transactional
    public int sincronizarPrecios() {
        log.debug("PriceSyncService: iniciando sincronización de precios");
        List<String> actualizados = new ArrayList<>();

        try {
            Map<String, SheetsClient.SheetRow> sheetData = sheetsClient.readAllStock();

            for (Map.Entry<String, SheetsClient.SheetRow> entry : sheetData.entrySet()) {
                String sku = entry.getKey();
                BigDecimal precioSheets = entry.getValue().precio();

                if (precioSheets == null || precioSheets.compareTo(BigDecimal.ZERO) <= 0) continue;

                StockItem item = stockRepository.findBySku(sku).orElse(null);
                if (item == null) continue;

                // Verificar si el precio cambió más del umbral de tolerancia
                if (!hayCambioPrecio(item.getPrecio(), precioSheets)) continue;

                log.info("PriceSyncService: cambio de precio detectado sku={} {} → {}",
                        sku, item.getPrecio(), precioSheets);

                boolean mlOk = actualizarPrecioML(item, precioSheets);
                boolean tangoOk = actualizarPrecioTango(item, precioSheets);

                if (mlOk || tangoOk) {
                    item.setPrecio(precioSheets);
                    stockRepository.save(item);
                    actualizados.add(sku);
                }
            }

        } catch (Exception e) {
            log.error("PriceSyncService: error en sincronización de precios: {}", e.getMessage(), e);
        }

        if (!actualizados.isEmpty()) {
            log.info("PriceSyncService: {} precios actualizados: {}", actualizados.size(), actualizados);
        }

        return actualizados.size();
    }

    private boolean actualizarPrecioML(StockItem item, BigDecimal nuevoPrecio) {
        if (item.getMlItemId() == null) return false;
        try {
            mlClient.updatePrice(item.getMlItemId(), nuevoPrecio);
            return true;
        } catch (Exception e) {
            log.error("PriceSyncService: error actualizando precio ML sku={}: {}", item.getSku(), e.getMessage());
            return false;
        }
    }

    private boolean actualizarPrecioTango(StockItem item, BigDecimal nuevoPrecio) {
        String tangoSku = item.getTangoProductoId() != null ? item.getTangoProductoId() : item.getSku();
        try {
            tangoClient.updatePrice(tangoSku, nuevoPrecio);
            return true;
        } catch (Exception e) {
            log.error("PriceSyncService: error actualizando precio Tango sku={}: {}", item.getSku(), e.getMessage());
            return false;
        }
    }

    private boolean hayCambioPrecio(BigDecimal precioActual, BigDecimal nuevoPrecio) {
        if (precioActual == null) return true;
        BigDecimal diff = precioActual.subtract(nuevoPrecio).abs();
        BigDecimal tolerancia = precioActual.multiply(TOLERANCIA_PORCENTUAL);
        return diff.compareTo(tolerancia) > 0;
    }
}
