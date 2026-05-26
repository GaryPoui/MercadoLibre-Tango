package com.tuempresa.stocksync.scheduler;

import com.tuempresa.stocksync.model.dto.StockAnalyticsData;
import com.tuempresa.stocksync.service.AIAnalysisService;
import com.tuempresa.stocksync.service.StockAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.analysis.enabled", havingValue = "true", matchIfMissing = true)
public class AIAnalysisScheduler {

    private final StockAnalyticsService analyticsService;
    private final AIAnalysisService aiAnalysisService;

    /**
     * Análisis periódico con IA: calcula métricas de todos los productos,
     * las envía al modelo y genera alertas + notificaciones automáticamente.
     * Por defecto cada 1 hora.
     */
    @Scheduled(fixedDelayString = "${ai.analysis.scheduler-interval-ms:3600000}")
    public void runAnalysis() {
        log.info("Scheduler IA: iniciando análisis automático de stock");
        try {
            List<StockAnalyticsData> metricas = analyticsService.calcularMetricas();
            if (metricas.isEmpty()) {
                log.info("Scheduler IA: sin productos para analizar");
                return;
            }

            long criticos = metricas.stream()
                    .filter(m -> m.nivelRiesgo() != StockAnalyticsData.RiskLevel.OK)
                    .count();

            log.info("Scheduler IA: {} productos totales, {} requieren atención", metricas.size(), criticos);

            aiAnalysisService.analizarStock(metricas);

            log.info("Scheduler IA: análisis completado");

        } catch (Exception e) {
            log.error("Error en scheduler de análisis IA: {}", e.getMessage(), e);
        }
    }
}
