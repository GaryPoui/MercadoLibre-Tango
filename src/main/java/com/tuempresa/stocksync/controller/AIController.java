package com.tuempresa.stocksync.controller;

import com.tuempresa.stocksync.model.StockAlert;
import com.tuempresa.stocksync.model.dto.AIAnalysisResult;
import com.tuempresa.stocksync.model.dto.StockAnalyticsData;
import com.tuempresa.stocksync.repository.StockAlertRepository;
import com.tuempresa.stocksync.service.AIAnalysisService;
import com.tuempresa.stocksync.service.StockAnalyticsService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class AIController {

    private final AIAnalysisService aiAnalysisService;
    private final StockAnalyticsService analyticsService;
    private final StockAlertRepository alertRepository;

    /**
     * Ejecuta un análisis completo de stock con IA ahora mismo.
     * Útil para forzar el análisis sin esperar el scheduler.
     */
    @PostMapping("/analizar")
    public ResponseEntity<AIAnalysisResult> analizarAhora() {
        List<StockAnalyticsData> metricas = analyticsService.calcularMetricas();
        AIAnalysisResult resultado = aiAnalysisService.analizarStock(metricas);
        return ResponseEntity.ok(resultado);
    }

    /**
     * Devuelve las métricas calculadas de todos los productos (sin llamar a la IA).
     * Útil para ver el estado actual antes de analizar.
     */
    @GetMapping("/metricas")
    public List<StockAnalyticsData> getMetricas() {
        return analyticsService.calcularMetricas();
    }

    /**
     * Devuelve solo los productos en riesgo (sin llamar a la IA).
     */
    @GetMapping("/metricas/en-riesgo")
    public List<StockAnalyticsData> getMetricasEnRiesgo() {
        return analyticsService.calcularMetricasEnRiesgo();
    }

    /**
     * Consulta en lenguaje natural sobre el stock.
     * Ej: GET /api/ia/consultar?q=¿Qué productos necesito reponer esta semana?
     */
    @GetMapping("/consultar")
    public ResponseEntity<Map<String, String>> consultar(
            @RequestParam("q") @NotBlank String pregunta) {
        List<StockAnalyticsData> metricas = analyticsService.calcularMetricas();
        String respuesta = aiAnalysisService.consultarEnLenguajeNatural(pregunta, metricas);
        return ResponseEntity.ok(Map.of(
                "pregunta", pregunta,
                "respuesta", respuesta,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Lista las alertas activas (pendientes de resolver).
     */
    @GetMapping("/alertas")
    public List<StockAlert> getAlertasActivas() {
        return alertRepository.findByEstadoOrderByCreatedAtDesc(StockAlert.AlertStatus.PENDIENTE);
    }

    /**
     * Lista todas las alertas paginadas.
     */
    @GetMapping("/alertas/todas")
    public Page<StockAlert> getAllAlertas(Pageable pageable) {
        return alertRepository.findAll(pageable);
    }

    /**
     * Alertas de un SKU específico.
     */
    @GetMapping("/alertas/{sku}")
    public Page<StockAlert> getAlertasBySku(@PathVariable String sku, Pageable pageable) {
        return alertRepository.findBySkuOrderByCreatedAtDesc(sku, pageable);
    }

    /**
     * Marca una alerta como resuelta.
     */
    @PatchMapping("/alertas/{id}/resolver")
    public ResponseEntity<StockAlert> resolverAlerta(@PathVariable Long id) {
        return alertRepository.findById(id).map(alerta -> {
            alerta.setEstado(StockAlert.AlertStatus.RESUELTA);
            alerta.setResolvedAt(LocalDateTime.now());
            return ResponseEntity.ok(alertRepository.save(alerta));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Marca una alerta como ignorada.
     */
    @PatchMapping("/alertas/{id}/ignorar")
    public ResponseEntity<StockAlert> ignorarAlerta(@PathVariable Long id) {
        return alertRepository.findById(id).map(alerta -> {
            alerta.setEstado(StockAlert.AlertStatus.IGNORADA);
            return ResponseEntity.ok(alertRepository.save(alerta));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Resumen del estado de las alertas de IA.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        LocalDateTime hace24hs = LocalDateTime.now().minusHours(24);
        long pendientes = alertRepository.countByEstadoAndCreatedAtAfter(
                StockAlert.AlertStatus.PENDIENTE, hace24hs);
        long notificadas = alertRepository.countByEstadoAndCreatedAtAfter(
                StockAlert.AlertStatus.NOTIFICADA, hace24hs);

        return ResponseEntity.ok(Map.of(
                "alertasUltimas24hs", Map.of(
                        "pendientes", pendientes,
                        "notificadas", notificadas
                ),
                "productosEnRiesgo", analyticsService.calcularMetricasEnRiesgo().size(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
