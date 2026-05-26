package com.tuempresa.stocksync.service;

import com.tuempresa.stocksync.model.StockAlert;
import com.tuempresa.stocksync.model.dto.AIAnalysisResult;
import com.tuempresa.stocksync.model.dto.AgentTaskResult;
import com.tuempresa.stocksync.model.dto.StockAnalyticsData;
import com.tuempresa.stocksync.repository.StockAlertRepository;
import com.tuempresa.stocksync.service.agent.AgentOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fachada del sistema de análisis de IA.
 * Delega el análisis al AgentOrchestrator (Planner → Executor → Checker)
 * y se encarga de convertir los resultados en alertas persistidas.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AIAnalysisService {

    private final AgentOrchestrator agentOrchestrator;
    private final StockAlertRepository alertRepository;
    private final NotificationService notificationService;
    private final ChatClient chatClient;

    /**
     * Punto de entrada principal.
     * Ejecuta el pipeline multi-agente y persiste las alertas resultantes.
     */
    @Transactional
    public AIAnalysisResult analizarStock(List<StockAnalyticsData> metricas) {
        if (metricas.isEmpty()) {
            log.info("AIAnalysisService: sin productos para analizar");
            return new AIAnalysisResult();
        }

        // Delegar al orquestador: Planner → Executor → Checker
        List<AgentTaskResult> resultados = agentOrchestrator.ejecutar(metricas);

        // Convertir resultados en alertas y notificaciones
        AIAnalysisResult resumen = new AIAnalysisResult();
        List<AIAnalysisResult.ItemAnalysis> items = resultados.stream()
                .map(this::toItemAnalysis)
                .toList();
        resumen.setItems(items);
        resumen.setResumenGeneral(buildResumen(resultados));

        // Persistir alertas para los resultados que lo requieren
        persistirAlertas(resultados, metricas);

        return resumen;
    }

    /**
     * Consulta libre en lenguaje natural sobre el inventario.
     * No usa el pipeline multi-agente — es una interacción directa conversacional.
     */
    public String consultarEnLenguajeNatural(String pregunta, List<StockAnalyticsData> metricas) {
        String contexto = buildContexto(metricas);

        return chatClient.prompt()
                .system("Sos un asistente experto en gestión de inventario para un negocio en MercadoLibre Argentina. " +
                        "Respondé de forma clara y concisa en español.")
                .user("""
                        Datos actuales de stock:
                        %s
                        
                        Pregunta: %s
                        """.formatted(contexto, pregunta))
                .call()
                .content();
    }

    // ─── Privados ────────────────────────────────────────────────────────────────

    private void persistirAlertas(List<AgentTaskResult> resultados, List<StockAnalyticsData> metricas) {
        for (AgentTaskResult r : resultados) {
            if (!r.isRequiereNotificacion()) continue;

            StockAlert.AlertType tipo = parseTipoAlerta(r.getTipoAlerta());

            // Evitar duplicados del mismo tipo para el mismo SKU en 6 horas
            boolean yaExiste = alertRepository.existsBySkuAndTipoAndCreatedAtAfter(
                    r.getSku(), tipo, LocalDateTime.now().minusHours(6));
            if (yaExiste) continue;

            String nombre = metricas.stream()
                    .filter(m -> m.sku().equals(r.getSku()))
                    .map(StockAnalyticsData::nombre)
                    .findFirst().orElse(r.getSku());

            int stockActual = metricas.stream()
                    .filter(m -> m.sku().equals(r.getSku()))
                    .map(StockAnalyticsData::stockActual)
                    .findFirst().orElse(0);

            String observacion = r.isBajaPrecision()
                    ? "[⚠️ Baja precisión tras " + r.getIntentos() + " intentos] " + r.getObservacion()
                    : r.getObservacion();

            StockAlert alerta = StockAlert.builder()
                    .sku(r.getSku())
                    .nombre(nombre)
                    .tipo(tipo)
                    .estado(StockAlert.AlertStatus.PENDIENTE)
                    .stockActual(stockActual)
                    .diasRestantes(r.getDiasRestantesEstimados())
                    .cantidadSugerida(r.getCantidadReponerSugerida())
                    .observacionIA(observacion)
                    .mensaje(String.format("[%s] %s — ~%d días restantes. Reponer: %d uds. %s",
                            r.getNivelRiesgo(), nombre,
                            r.getDiasRestantesEstimados(),
                            r.getCantidadReponerSugerida(),
                            r.getObservacion()))
                    .build();

            alertRepository.save(alerta);
            log.info("Alerta persistida: sku={} tipo={} intentos={} bajaPrecision={}",
                    r.getSku(), tipo, r.getIntentos(), r.isBajaPrecision());

            notificationService.notificar(alerta);
        }
    }

    private AIAnalysisResult.ItemAnalysis toItemAnalysis(AgentTaskResult r) {
        AIAnalysisResult.ItemAnalysis item = new AIAnalysisResult.ItemAnalysis();
        item.setSku(r.getSku());
        item.setNivelRiesgo(r.getNivelRiesgo());
        item.setDiasRestantesEstimados(r.getDiasRestantesEstimados());
        item.setCantidadReponerSugerida(r.getCantidadReponerSugerida());
        item.setObservacion(r.getObservacion());
        item.setRequiereNotificacion(r.isRequiereNotificacion());
        item.setTipoAlerta(r.getTipoAlerta());
        return item;
    }

    private String buildResumen(List<AgentTaskResult> resultados) {
        long criticos  = resultados.stream().filter(r -> "CRITICO".equals(r.getNivelRiesgo()) || "AGOTADO".equals(r.getNivelRiesgo())).count();
        long bajaPrecision = resultados.stream().filter(AgentTaskResult::isBajaPrecision).count();
        return String.format("Pipeline multi-agente completado: %d tareas analizadas, %d críticas/agotadas, %d con baja precisión.",
                resultados.size(), criticos, bajaPrecision);
    }

    private String buildContexto(List<StockAnalyticsData> metricas) {
        StringBuilder sb = new StringBuilder();
        for (StockAnalyticsData m : metricas) {
            sb.append(String.format("- %s (SKU: %s): stock=%d, ventas/día=%.2f, días cobertura=%d, riesgo=%s\n",
                    m.nombre(), m.sku(), m.stockActual(), m.ventasPorDia(), m.diasDeStock(), m.nivelRiesgo()));
        }
        return sb.toString();
    }

    private StockAlert.AlertType parseTipoAlerta(String tipo) {
        try {
            return StockAlert.AlertType.valueOf(tipo);
        } catch (Exception e) {
            return StockAlert.AlertType.STOCK_BAJO;
        }
    }
}


@Service
@Slf4j
@RequiredArgsConstructor
public class AIAnalysisService {

    private final ChatClient chatClient;
    private final StockAlertRepository alertRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            Sos un experto en gestión de inventario para un negocio de e-commerce en Argentina
            que vende en MercadoLibre. Tu tarea es analizar el stock actual y el historial de ventas
            de cada producto y determinar cuáles necesitan atención inmediata.
            
            Para cada producto devolvé un análisis en el siguiente formato JSON estricto:
            {
              "items": [
                {
                  "sku": "string",
                  "nivelRiesgo": "OK|BAJO|CRITICO|AGOTADO",
                  "diasRestantesEstimados": number,
                  "cantidadReponerSugerida": number,
                  "observacion": "string con explicación breve",
                  "requiereNotificacion": boolean,
                  "tipoAlerta": "STOCK_BAJO|AGOTAMIENTO_CERCANO|SIN_STOCK|ANOMALIA_VENTAS|REPOSICION_SUGERIDA"
                }
              ],
              "resumenGeneral": "string con resumen ejecutivo"
            }
            
            Criterios:
            - AGOTADO: stock = 0 o menos de 3 días de cobertura
            - CRITICO: 3 a 7 días de cobertura
            - BAJO: 7 a 14 días de cobertura
            - OK: más de 14 días
            - requiereNotificacion = true para CRITICO y AGOTADO, o cuando detectés anomalías
            - cantidadReponerSugerida debe cubrir al menos 30 días de ventas
            - Si un producto no tuvo ventas en los últimos 30 días, indicarlo en la observación
            - Detectá anomalías: caídas bruscas de stock que no corresponden al ritmo de ventas
            
            Respondé ÚNICAMENTE con el JSON, sin texto adicional ni bloques de código.
            """;

    /**
     * Analiza una lista de métricas de stock y genera alertas usando la IA.
     * Solo envía a la IA los productos que tienen actividad o riesgo — no todos.
     */
    @Transactional
    public AIAnalysisResult analizarStock(List<StockAnalyticsData> metricas) {
        if (metricas.isEmpty()) {
            log.info("No hay productos para analizar");
            return new AIAnalysisResult();
        }

        String prompt = buildPrompt(metricas);
        log.info("Enviando análisis de {} productos a la IA", metricas.size());

        try {
            AIAnalysisResult resultado = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .entity(AIAnalysisResult.class);

            if (resultado != null && resultado.getItems() != null) {
                procesarResultado(resultado, metricas);
            }

            return resultado;

        } catch (Exception e) {
            log.error("Error en análisis IA: {}", e.getMessage(), e);
            // Fallback: generar alertas básicas sin IA
            return generarAlertasFallback(metricas);
        }
    }

    /**
     * Realiza el análisis completo y devuelve las alertas generadas.
     */
    @Transactional
    public List<StockAlert> ejecutarAnalisisCompleto(List<StockAnalyticsData> metricas) {
        AIAnalysisResult resultado = analizarStock(metricas);
        return alertRepository.findByEstadoOrderByCreatedAtDesc(StockAlert.AlertStatus.PENDIENTE);
    }

    /**
     * Permite hacer una consulta libre en lenguaje natural sobre el stock.
     * Ej: "¿Qué productos necesito reponer antes del fin de semana?"
     */
    public String consultarEnLenguajeNatural(String pregunta, List<StockAnalyticsData> metricas) {
        String contexto = buildPrompt(metricas);
        String promptCompleto = """
                Datos actuales de stock:
                %s
                
                Pregunta del usuario: %s
                
                Respondé de forma clara y concisa en español, como si fueras un asistente de negocio.
                """.formatted(contexto, pregunta);

        return chatClient.prompt()
                .system("Sos un asistente experto en gestión de inventario para un negocio en MercadoLibre Argentina.")
                .user(promptCompleto)
                .call()
                .content();
    }

    // ─── Privados ───────────────────────────────────────────────────────────────

    private String buildPrompt(List<StockAnalyticsData> metricas) {
        StringBuilder sb = new StringBuilder("Analizá el siguiente inventario:\n\n");
        for (StockAnalyticsData m : metricas) {
            sb.append("- SKU: ").append(m.sku())
              .append(" | Nombre: ").append(m.nombre())
              .append(" | Stock actual: ").append(m.stockActual())
              .append(" | Stock mínimo: ").append(m.stockMinimo())
              .append(" | Ventas/día (30d): ").append(m.ventasPorDia())
              .append(" | Días de cobertura estimados: ").append(m.diasDeStock())
              .append(" | Total vendido últimos 30 días: ").append(m.totalVendidoUltimos30Dias())
              .append(" | Riesgo calculado: ").append(m.nivelRiesgo())
              .append("\n");
        }
        return sb.toString();
    }

    private void procesarResultado(AIAnalysisResult resultado, List<StockAnalyticsData> metricas) {
        for (AIAnalysisResult.ItemAnalysis item : resultado.getItems()) {
            if (!item.isRequiereNotificacion()) continue;

            StockAlert.AlertType tipo = parseTipoAlerta(item.getTipoAlerta());

            // Evitar duplicar alertas del mismo tipo en las últimas 6 horas
            boolean yaExiste = alertRepository.existsBySkuAndTipoAndCreatedAtAfter(
                    item.getSku(), tipo, LocalDateTime.now().minusHours(6));

            if (yaExiste) {
                log.debug("Alerta duplicada ignorada: sku={} tipo={}", item.getSku(), tipo);
                continue;
            }

            // Buscar nombre del producto
            String nombre = metricas.stream()
                    .filter(m -> m.sku().equals(item.getSku()))
                    .map(StockAnalyticsData::nombre)
                    .findFirst().orElse(item.getSku());

            StockAlert alerta = StockAlert.builder()
                    .sku(item.getSku())
                    .nombre(nombre)
                    .tipo(tipo)
                    .estado(StockAlert.AlertStatus.PENDIENTE)
                    .stockActual(metricas.stream()
                            .filter(m -> m.sku().equals(item.getSku()))
                            .map(StockAnalyticsData::stockActual)
                            .findFirst().orElse(0))
                    .diasRestantes(item.getDiasRestantesEstimados())
                    .cantidadSugerida(item.getCantidadReponerSugerida())
                    .observacionIA(item.getObservacion())
                    .mensaje(buildMensajeAlerta(item, nombre))
                    .build();

            alertRepository.save(alerta);
            log.info("Alerta generada por IA: sku={} tipo={}", item.getSku(), tipo);

            // Notificar
            notificationService.notificar(alerta);
        }
    }

    private String buildMensajeAlerta(AIAnalysisResult.ItemAnalysis item, String nombre) {
        return String.format(
                "[%s] %s — Stock: aproximadamente %d días restantes. Reponer: %d unidades. %s",
                item.getNivelRiesgo(), nombre,
                item.getDiasRestantesEstimados(),
                item.getCantidadReponerSugerida(),
                item.getObservacion()
        );
    }

    private StockAlert.AlertType parseTipoAlerta(String tipo) {
        try {
            return StockAlert.AlertType.valueOf(tipo);
        } catch (Exception e) {
            return StockAlert.AlertType.STOCK_BAJO;
        }
    }

    /**
     * Fallback sin IA: genera alertas básicas por umbral de stock mínimo.
     */
    private AIAnalysisResult generarAlertasFallback(List<StockAnalyticsData> metricas) {
        log.warn("Usando análisis fallback (sin IA)");
        for (StockAnalyticsData m : metricas) {
            if (m.nivelRiesgo() == StockAnalyticsData.RiskLevel.OK) continue;

            StockAlert.AlertType tipo = switch (m.nivelRiesgo()) {
                case AGOTADO -> StockAlert.AlertType.SIN_STOCK;
                case CRITICO -> StockAlert.AlertType.AGOTAMIENTO_CERCANO;
                case BAJO    -> StockAlert.AlertType.STOCK_BAJO;
                default      -> StockAlert.AlertType.REPOSICION_SUGERIDA;
            };

            boolean yaExiste = alertRepository.existsBySkuAndTipoAndCreatedAtAfter(
                    m.sku(), tipo, LocalDateTime.now().minusHours(6));
            if (yaExiste) continue;

            StockAlert alerta = StockAlert.builder()
                    .sku(m.sku())
                    .nombre(m.nombre())
                    .tipo(tipo)
                    .estado(StockAlert.AlertStatus.PENDIENTE)
                    .stockActual(m.stockActual())
                    .diasRestantes(m.diasDeStock())
                    .mensaje(String.format("[%s] %s — Stock actual: %d unidades (%d días estimados)",
                            m.nivelRiesgo(), m.nombre(), m.stockActual(), m.diasDeStock()))
                    .build();

            alertRepository.save(alerta);
            notificationService.notificar(alerta);
        }

        AIAnalysisResult fallback = new AIAnalysisResult();
        fallback.setResumenGeneral("Análisis fallback por error en servicio de IA.");
        return fallback;
    }
}
