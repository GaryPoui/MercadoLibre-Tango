package com.tuempresa.stocksync.service.agent;

import com.tuempresa.stocksync.model.dto.AgentTask;
import com.tuempresa.stocksync.model.dto.AgentTaskResult;
import com.tuempresa.stocksync.model.dto.StockAnalyticsData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * AGENTE 2 — EJECUTOR
 *
 * Recibe UNA tarea indivisible del Planner y la ejecuta:
 * analiza en profundidad UN producto específico.
 *
 * Si el Checker rechaza el resultado, el Orchestrator llama de nuevo
 * a este agente pasándole el feedback de corrección.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExecutorAgent {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            Sos un analista experto en gestión de inventario para un negocio de MercadoLibre Argentina.
            
            Vas a recibir los datos de UN solo producto y tu tarea es hacer un análisis detallado.
            
            Devolvé ÚNICAMENTE este JSON, sin texto adicional ni bloques de código:
            {
              "sku": "string",
              "nivelRiesgo": "OK|BAJO|CRITICO|AGOTADO",
              "diasRestantesEstimados": number,
              "cantidadReponerSugerida": number,
              "observacion": "string detallado",
              "requiereNotificacion": boolean,
              "tipoAlerta": "STOCK_BAJO|AGOTAMIENTO_CERCANO|SIN_STOCK|ANOMALIA_VENTAS|REPOSICION_SUGERIDA"
            }
            
            Reglas de coherencia que DEBÉS respetar:
            - AGOTADO: stock=0 o diasRestantesEstimados <= 3
            - CRITICO: diasRestantesEstimados entre 4 y 7
            - BAJO: diasRestantesEstimados entre 8 y 14
            - OK: diasRestantesEstimados > 14
            - requiereNotificacion=true SIEMPRE que nivelRiesgo sea CRITICO o AGOTADO
            - cantidadReponerSugerida = al menos (ventasPorDia * 30), redondeado arriba
            - Si ventasPorDia=0, diasRestantesEstimados=9999 y nivelRiesgo debería ser OK (salvo que stock=0)
            - tipoAlerta debe ser coherente con nivelRiesgo:
              * AGOTADO → SIN_STOCK
              * CRITICO → AGOTAMIENTO_CERCANO
              * BAJO → STOCK_BAJO o REPOSICION_SUGERIDA
              * OK con anomalía → ANOMALIA_VENTAS
            """;

    /**
     * Ejecuta el análisis de una tarea.
     *
     * @param task     Tarea a ejecutar
     * @param feedback Feedback del Checker del intento anterior (null en primer intento)
     * @return Resultado del análisis
     */
    public AgentTaskResult ejecutar(AgentTask task, String feedback) {
        log.debug("ExecutorAgent: analizando sku={} prioridad={}", task.sku(), task.prioridad());

        String userMessage = buildUserMessage(task, feedback);

        try {
            AgentTaskResult result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userMessage)
                    .call()
                    .entity(AgentTaskResult.class);

            if (result == null) {
                log.warn("ExecutorAgent: resultado nulo para sku={}", task.sku());
                return buildFallbackResult(task);
            }

            // Garantizar que el SKU sea correcto (la IA a veces lo omite)
            result.setSku(task.sku());
            return result;

        } catch (Exception e) {
            log.error("ExecutorAgent: error analizando sku={}: {}", task.sku(), e.getMessage());
            return buildFallbackResult(task);
        }
    }

    private String buildUserMessage(AgentTask task, String feedback) {
        StockAnalyticsData m = task.metricas();

        StringBuilder sb = new StringBuilder();
        sb.append("Analizá el siguiente producto:\n\n");
        sb.append(String.format("SKU: %s\n", m.sku()));
        sb.append(String.format("Nombre: %s\n", m.nombre()));
        sb.append(String.format("Stock actual: %d unidades\n", m.stockActual()));
        sb.append(String.format("Stock mínimo configurado: %d unidades\n", m.stockMinimo()));
        sb.append(String.format("Ventas promedio por día (últimos 30 días): %.2f unidades/día\n", m.ventasPorDia()));
        sb.append(String.format("Total vendido en los últimos 30 días: %d unidades\n", m.totalVendidoUltimos30Dias()));
        sb.append(String.format("Días de cobertura estimados (cálculo simple): %d días\n", m.diasDeStock()));
        sb.append(String.format("Prioridad asignada por el planificador: %s\n", task.prioridad()));
        sb.append(String.format("Razón de análisis: %s\n", task.razonAnalisis()));

        if (task.contextExtra() != null && !task.contextExtra().isBlank()) {
            sb.append(String.format("Contexto adicional: %s\n", task.contextExtra()));
        }

        if (feedback != null && !feedback.isBlank()) {
            sb.append("\n⚠️ CORRECCIÓN REQUERIDA (intento anterior rechazado):\n");
            sb.append(feedback).append("\n");
            sb.append("Por favor corregí los campos indicados y asegurate de respetar las reglas de coherencia.\n");
        }

        return sb.toString();
    }

    private AgentTaskResult buildFallbackResult(AgentTask task) {
        StockAnalyticsData m = task.metricas();
        AgentTaskResult fallback = new AgentTaskResult();
        fallback.setSku(task.sku());
        fallback.setNivelRiesgo(m.nivelRiesgo().name());
        fallback.setDiasRestantesEstimados(m.diasDeStock());
        fallback.setCantidadReponerSugerida((int) Math.ceil(m.ventasPorDia() * 30));
        fallback.setObservacion("Análisis fallback por error en servicio de IA.");
        fallback.setRequiereNotificacion(m.nivelRiesgo() != StockAnalyticsData.RiskLevel.OK);
        fallback.setTipoAlerta(m.nivelRiesgo() == StockAnalyticsData.RiskLevel.AGOTADO
                ? "SIN_STOCK" : "STOCK_BAJO");
        fallback.setBajaPrecision(true);
        return fallback;
    }
}
