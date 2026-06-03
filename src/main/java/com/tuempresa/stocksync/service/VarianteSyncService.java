package com.tuempresa.stocksync.service;

import com.tuempresa.stocksync.adapter.MLClient;
import com.tuempresa.stocksync.model.StockItem;
import com.tuempresa.stocksync.model.Variante;
import com.tuempresa.stocksync.repository.VarianteRepository;
import com.tuempresa.stocksync.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sincroniza variantes (stock, precio y atributos) hacia MercadoLibre.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VarianteSyncService {

    private final VarianteRepository varianteRepository;
    private final StockRepository stockRepository;
    private final MLClient mlClient;
    private final SyncOutboxService outboxService;

    /**
     * Sincroniza el stock de todas las variantes activas hacia ML.
     * Retorna la cantidad de variantes actualizadas.
     */
    @Transactional(readOnly = true)
    public int syncVariantesStock() {
        log.debug("VarianteSyncService: iniciando sync de stock de variantes");
        int actualizados = 0;

        List<StockItem> itemsConML = stockRepository.findByMlItemIdIsNotNull();

        for (StockItem padre : itemsConML) {
            List<Variante> variantes = varianteRepository
                    .findByStockItemPadre_SkuAndActivoTrue(padre.getSku());

            for (Variante variante : variantes) {
                if (variante.getMlVarianteId() == null) continue;

                try {
                    mlClient.updateVariationStock(
                            padre.getMlItemId(),
                            variante.getMlVarianteId(),
                            variante.getStock());
                    actualizados++;
                } catch (Exception e) {
                    log.error("Error sincronizando stock variante sku={} mlVarianteId={}: {}",
                            variante.getSku(), variante.getMlVarianteId(), e.getMessage());
                    outboxService.registrarPendiente(
                            variante.getSku(),
                            com.tuempresa.stocksync.model.SyncPendiente.DestinoSync.MERCADOLIBRE,
                            com.tuempresa.stocksync.model.SyncPendiente.TipoOperacion.UPDATE_STOCK,
                            String.valueOf(variante.getStock()));
                }
            }
        }

        if (actualizados > 0) {
            log.info("VarianteSyncService: {} variantes sincronizadas a ML", actualizados);
        }
        return actualizados;
    }

    /**
     * Sincroniza el precio de todas las variantes que tienen precioEspecial hacia ML.
     */
    @Transactional(readOnly = true)
    public int syncVariantesPrecios() {
        log.debug("VarianteSyncService: iniciando sync de precios de variantes");
        int actualizados = 0;

        List<StockItem> itemsConML = stockRepository.findByMlItemIdIsNotNull();

        for (StockItem padre : itemsConML) {
            List<Variante> variantes = varianteRepository
                    .findByStockItemPadre_SkuAndActivoTrue(padre.getSku());

            for (Variante variante : variantes) {
                if (variante.getMlVarianteId() == null) continue;
                if (variante.getPrecioEspecial() == null) continue;

                try {
                    mlClient.updateVariationPrice(
                            padre.getMlItemId(),
                            variante.getMlVarianteId(),
                            variante.getPrecioEspecial());
                    actualizados++;
                } catch (Exception e) {
                    log.error("Error sincronizando precio variante sku={}: {}",
                            variante.getSku(), e.getMessage());
                    outboxService.registrarPendiente(
                            variante.getSku(),
                            com.tuempresa.stocksync.model.SyncPendiente.DestinoSync.MERCADOLIBRE,
                            com.tuempresa.stocksync.model.SyncPendiente.TipoOperacion.UPDATE_PRICE,
                            variante.getPrecioEspecial().toPlainString());
                }
            }
        }

        if (actualizados > 0) {
            log.info("VarianteSyncService: {} precios de variantes sincronizados a ML", actualizados);
        }
        return actualizados;
    }

    /**
     * Sincroniza los atributos de una variante específica a ML.
     */
    public void syncAtributosVariante(Variante variante) {
        StockItem padre = variante.getStockItemPadre();
        if (padre.getMlItemId() == null || variante.getMlVarianteId() == null) {
            log.debug("Variante sku={} no tiene IDs de ML configurados", variante.getSku());
            return;
        }

        List<Map<String, String>> attrCombinations = new ArrayList<>();
        for (Map.Entry<String, String> entry : variante.getAtributos().entrySet()) {
            attrCombinations.add(Map.of(
                    "id", entry.getKey(),
                    "value_name", entry.getValue()
            ));
        }

        try {
            mlClient.updateVariationAttributes(
                    padre.getMlItemId(),
                    variante.getMlVarianteId(),
                    attrCombinations);
            log.info("Atributos sincronizados para variante sku={}", variante.getSku());
        } catch (Exception e) {
            log.error("Error sincronizando atributos variante sku={}: {}",
                    variante.getSku(), e.getMessage());
        }
    }
}
