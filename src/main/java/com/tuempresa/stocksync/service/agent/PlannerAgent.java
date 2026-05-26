package com.tuempresa.stocksync.service.agent;

import com.tuempresa.stocksync.model.dto.AgentTask;
import com.tuempresa.stocksync.model.dto.PlannerOutput;
import com.tuempresa.stocksync.model.dto.StockAnalyticsData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AGENTE 1 — PLANIFICADOR
 *
 * Recibe el estado completo del inventario y decide:
 * - Qué productos necesitan análisis profundo
 * - En qué orden de prioridad
 * - Por qué razón cada uno necesita atención
 *
 * No analiza los productos — solo planifica qué analizar y cómo.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PlannerAgent {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            Sos un planificador experto en gestión de inventario para un negocio de MercadoLibre Argentina.
            
            Tu única tarea es revisar el estado del inventario y decidir QUÉ productos necesitan
            un análisis profundo, en qué ORDEN de prioridad, y por qué.
            
            NO hagas el análisis vos mismo. Solo planificá las tareas para los analistas.
            
            Criterios de selección:
            - Incluí SIEMPRE productos con riesgo AGOTADO o CRITICO
            - Incluí productos BAJO si tienen alta velocidad de ventas (>5 unidades/día)
            - Incluí productos OK solo si detectás anomalías (ej: 0 ventas en 30 días con stock muy alto)
            - Omití productos OK sin anomalías — no necesitan atención
            
            Devolvé ÚNICAMENTE este JSON, sin texto adicional:
            {
              "tareas": [
                {
                  "sku": "string",
                  "prioridad": "ALTA|MEDIA|BAJA",
                  "razonAnalisis": "Breve justificación de por qué necesita análisis",
                  "contextExtra": "Contexto adicional útil para el analista (patrones, anomalías, etc.)"
                }
              ],
              "estrategiaGeneral": "Resumen de la situación del inventario y el enfoque de análisis"
            }
            
            Ordenar las tareas de mayor a menor prioridad.
            """;

    /**
     * Analiza el estado del inventario y genera el plan de tareas.
     *
     * @param metricas Lista completa de métricas de todos los productos
     * @return Lista de tareas ordenadas por prioridad
     */
    public List<AgentTask> planificar(List<StockAnalyticsData> metricas) {
        if (metricas.isEmpty()) return List.of();

        String prompt = buildPrompt(metricas);
        log.info("PlannerAgent: analizando {} productos", metricas.size());

        try {
            PlannerOutput output = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .entity(PlannerOutput.class);

            if (output == null || output.getTareas() == null || output.getTareas().isEmpty()) {
                log.info("PlannerAgent: sin tareas generadas — inventario saludable");
                return List.of();
            }

            log.info("PlannerAgent: {} tareas planificadas. Estrategia: {}",
                    output.getTareas().size(), output.getEstrategiaGeneral());

            // Mapear SKUs a métricas para enriquecer cada tarea
            Map<String, StockAnalyticsData> metricasPorSku = metricas.stream()
                    .collect(Collectors.toMap(StockAnalyticsData::sku, Function.identity()));

            return output.getTareas().stream()
                    .filter(t -> metricasPorSku.containsKey(t.getSku()))
                    .map(t -> new AgentTask(
                            t.getSku(),
                            metricasPorSku.get(t.getSku()).nombre(),
                            metricasPorSku.get(t.getSku()),
                            t.getPrioridad(),
                            t.getRazonAnalisis(),
                            t.getContextExtra()
                    ))
                    .toList();

        } catch (Exception e) {
            log.error("PlannerAgent: error al planificar: {}", e.getMessage(), e);
            // Fallback: crear tareas para todos los productos en riesgo
            return fallbackPlan(metricas);
        }
    }

    private String buildPrompt(List<StockAnalyticsData> metricas) {
        StringBuilder sb = new StringBuilder("Estado actual del inventario:\n\n");
        for (StockAnalyticsData m : metricas) {
            sb.append(String.format(
                    "SKU: %-15s | %-30s | Stock: %4d | Ventas/día: %5.2f | Días cobertura: %4d | Riesgo: %s\n",
                    m.sku(), m.nombre(), m.stockActual(), m.ventasPorDia(),
                    m.diasDeStock(), m.nivelRiesgo()
            ));
        }
        return sb.toString();
    }

    private List<AgentTask> fallbackPlan(List<StockAnalyticsData> metricas) {
        log.warn("PlannerAgent: usando fallback — incluyendo todos los productos en riesgo");
        return metricas.stream()
                .filter(m -> m.nivelRiesgo() != StockAnalyticsData.RiskLevel.OK)
                .map(m -> new AgentTask(
                        m.sku(), m.nombre(), m,
                        m.nivelRiesgo() == StockAnalyticsData.RiskLevel.AGOTADO ? "ALTA" : "MEDIA",
                        "Riesgo detectado: " + m.nivelRiesgo(),
                        null
                ))
                .toList();
    }
}
