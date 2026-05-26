package com.tuempresa.stocksync.service;

import com.tuempresa.stocksync.model.StockItem;
import com.tuempresa.stocksync.model.SyncLog;
import com.tuempresa.stocksync.model.dto.StockAnalyticsData;
import com.tuempresa.stocksync.repository.StockRepository;
import com.tuempresa.stocksync.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockAnalyticsService {

    private final StockRepository stockRepository;
    private final SyncLogRepository syncLogRepository;

    @Value("${ai.analysis.historial-dias:30}")
    private int historialDias;

    @Value("${ai.analysis.dias-cobertura-alerta:7}")
    private int diasCoberturaAlerta;

    /**
     * Calcula las métricas de todos los productos y determina su nivel de riesgo.
     * Este método transforma el historial de sincronizaciones en datos analíticos
     * que luego se envían a la IA para su interpretación.
     */
    public List<StockAnalyticsData> calcularMetricas() {
        List<StockItem> items = stockRepository.findAll();
        return items.stream()
                .map(this::calcularMetricaItem)
                .toList();
    }

    /**
     * Calcula las métricas para un producto específico.
     */
    public StockAnalyticsData calcularMetricaItem(StockItem item) {
        LocalDateTime desde = LocalDateTime.now().minusDays(historialDias);

        // Sumar todas las ventas (eventos de MERCADOLIBRE con estado OK) del período
        List<SyncLog> logs = syncLogRepository
                .findByEstadoAndCreatedAtAfter(SyncLog.SyncStatus.OK, desde)
                .stream()
                .filter(log -> item.getSku().equals(log.getSku())
                        && log.getOrigen() == SyncLog.SyncOrigin.MERCADOLIBRE
                        && log.getStockAnterior() != null
                        && log.getStockNuevo() != null
                        && log.getStockAnterior() > log.getStockNuevo())
                .toList();

        int totalVendido = logs.stream()
                .mapToInt(log -> log.getStockAnterior() - log.getStockNuevo())
                .sum();

        double ventasPorDia = historialDias > 0 ? (double) totalVendido / historialDias : 0;

        int diasDeStock = ventasPorDia > 0
                ? (int) (item.getStock() / ventasPorDia)
                : Integer.MAX_VALUE; // sin ventas = stock "infinito"

        StockAnalyticsData.RiskLevel riesgo = calcularRiesgo(item.getStock(), diasDeStock);

        return new StockAnalyticsData(
                item.getSku(),
                item.getNombre(),
                item.getStock(),
                item.getStockMinimo() != null ? item.getStockMinimo() : 0,
                Math.round(ventasPorDia * 100.0) / 100.0,
                diasDeStock == Integer.MAX_VALUE ? 9999 : diasDeStock,
                totalVendido,
                riesgo
        );
    }

    /**
     * Determina el nivel de riesgo basado en días de cobertura y stock actual.
     */
    private StockAnalyticsData.RiskLevel calcularRiesgo(int stockActual, int diasDeStock) {
        if (stockActual <= 0) return StockAnalyticsData.RiskLevel.AGOTADO;
        if (diasDeStock <= 3) return StockAnalyticsData.RiskLevel.AGOTADO;
        if (diasDeStock <= diasCoberturaAlerta) return StockAnalyticsData.RiskLevel.CRITICO;
        if (diasDeStock <= diasCoberturaAlerta * 2) return StockAnalyticsData.RiskLevel.BAJO;
        return StockAnalyticsData.RiskLevel.OK;
    }

    /**
     * Devuelve solo los productos que requieren atención (riesgo != OK).
     */
    public List<StockAnalyticsData> calcularMetricasEnRiesgo() {
        return calcularMetricas().stream()
                .filter(m -> m.nivelRiesgo() != StockAnalyticsData.RiskLevel.OK)
                .toList();
    }
}
