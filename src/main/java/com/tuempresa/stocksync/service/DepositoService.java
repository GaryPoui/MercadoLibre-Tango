package com.tuempresa.stocksync.service;

import com.tuempresa.stocksync.model.Deposito;
import com.tuempresa.stocksync.model.StockItem;
import com.tuempresa.stocksync.model.StockPorDeposito;
import com.tuempresa.stocksync.repository.DepositoRepository;
import com.tuempresa.stocksync.repository.StockPorDepositoRepository;
import com.tuempresa.stocksync.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DepositoService {

    private final DepositoRepository depositoRepository;
    private final StockPorDepositoRepository stockPorDepositoRepository;
    private final StockRepository stockRepository;

    /**
     * Stock total de un SKU sumando todos los depósitos.
     */
    public int getStockTotal(String sku) {
        Integer total = stockPorDepositoRepository.sumStockTotalBySku(sku);
        return total != null ? total : 0;
    }

    /**
     * Stock disponible en MercadoLibre: suma solo los depósitos con exportaAML=true.
     */
    public int getStockParaML(String sku) {
        Integer total = stockPorDepositoRepository.sumStockMLBysku(sku);
        return total != null ? total : 0;
    }

    /**
     * Distribución de stock por depósito para un SKU.
     */
    public List<StockPorDeposito> getDistribucionStock(String sku) {
        return stockPorDepositoRepository.findByStockItem_Sku(sku);
    }

    /**
     * Actualiza el stock en un depósito específico y sincroniza el total en StockItem.
     */
    @Transactional
    public StockPorDeposito actualizarStockEnDeposito(String sku, Long depositoId, int nuevoStock) {
        StockItem item = stockRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("SKU no encontrado: " + sku));
        Deposito deposito = depositoRepository.findById(depositoId)
                .orElseThrow(() -> new IllegalArgumentException("Depósito no encontrado: " + depositoId));

        StockPorDeposito spd = stockPorDepositoRepository
                .findByStockItem_SkuAndDeposito_Id(sku, depositoId)
                .orElseGet(() -> StockPorDeposito.builder()
                        .stockItem(item)
                        .deposito(deposito)
                        .stock(0)
                        .build());

        spd.setStock(nuevoStock);
        stockPorDepositoRepository.save(spd);

        // Sincronizar el total consolidado en StockItem
        int totalConsolidado = getStockTotal(sku);
        item.setStock(totalConsolidado);
        stockRepository.save(item);

        log.info("Stock actualizado en depósito: sku={} deposito={} nuevoStock={} totalConsolidado={}",
                sku, deposito.getNombre(), nuevoStock, totalConsolidado);

        return spd;
    }

    /**
     * Descuenta stock de un depósito específico o del que tenga más stock si no se especifica.
     */
    @Transactional
    public void descontarStock(String sku, int cantidad, Long depositoId) {
        if (depositoId != null) {
            StockPorDeposito spd = stockPorDepositoRepository
                    .findByStockItem_SkuAndDeposito_Id(sku, depositoId)
                    .orElseThrow(() -> new IllegalStateException("No hay stock de " + sku + " en el depósito " + depositoId));

            int nuevoStock = Math.max(0, spd.getStock() - cantidad);
            actualizarStockEnDeposito(sku, depositoId, nuevoStock);
        } else {
            // Sin depósito específico: descontar del que más stock tiene
            List<StockPorDeposito> stocks = stockPorDepositoRepository.findByStockItem_Sku(sku);
            stocks.stream()
                    .filter(s -> s.getDeposito().isActivo())
                    .max(java.util.Comparator.comparingInt(StockPorDeposito::getStock))
                    .ifPresent(spd -> {
                        int nuevoStock = Math.max(0, spd.getStock() - cantidad);
                        actualizarStockEnDeposito(sku, spd.getDeposito().getId(), nuevoStock);
                    });
        }
    }

    /**
     * Resumen de stock de todos los depósitos.
     */
    public Map<String, Integer> getResumenPorDeposito(Long depositoId) {
        Deposito deposito = depositoRepository.findById(depositoId)
                .orElseThrow(() -> new IllegalArgumentException("Depósito no encontrado: " + depositoId));

        return stockPorDepositoRepository.findAll().stream()
                .filter(s -> s.getDeposito().getId().equals(depositoId))
                .collect(Collectors.toMap(
                        s -> s.getStockItem().getSku(),
                        StockPorDeposito::getStock
                ));
    }
}
