package com.tuempresa.stocksync.service.agent;

import com.tuempresa.stocksync.model.dto.AgentTask;
import com.tuempresa.stocksync.model.dto.AgentTaskResult;
import com.tuempresa.stocksync.model.dto.CheckResult;
import com.tuempresa.stocksync.model.dto.StockAnalyticsData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * ORQUESTADOR MULTI-AGENTE
 *
 * Coordina el pipeline completo:
 *
 *   1. PLANNER  → decide qué analizar y con qué prioridad
 *   2. EXECUTOR → analiza cada producto (tarea indivisible)
 *   3. CHECKER  → verifica el resultado
 *      ↳ si falla → EXECUTOR reintenta con el feedback (máx N intentos)
 *      ↳ si sigue fallando → se marca como bajaPrecision y se usa igual
 *
 * Las tareas del Executor corren en paralelo entre sí (son independientes).
 * El ciclo Executor→Checker es siempre secuencial dentro de cada tarea.
 *
 *   ┌─────────────────────────────────────────────────────────┐
 *   │                    AgentOrchestrator                    │
 *   │                                                         │
 *   │  metricas ──► PlannerAgent ──► [task1, task2, task3]   │
 *   │                                    │                    │
 *   │              ┌─────────────────────┤ paralelo           │
 *   │              ▼         ▼           ▼                    │
 *   │          ┌───────┐ ┌───────┐ ┌───────┐                 │
 *   │          │ loop  │ │ loop  │ │ loop  │                  │
 *   │          │E → C  │ │E → C  │ │E → C  │                 │
 *   │          └───┬───┘ └───┬───┘ └───┬───┘                 │
 *   │              └─────────┴─────────┘                      │
 *   │                        │                                │
 *   │               [results consolidados]                    │
 *   └─────────────────────────────────────────────────────────┘
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final PlannerAgent plannerAgent;
    private final ExecutorAgent executorAgent;
    private final CheckerAgent checkerAgent;
    private final Executor taskExecutor;

    @Value("${ai.agent.max-retries:3}")
    private int maxRetries;

    /**
     * Ejecuta el pipeline completo multi-agente.
     *
     * @param metricas Estado actual del inventario
     * @return Lista de resultados verificados (uno por tarea planificada)
     */
    public List<AgentTaskResult> ejecutar(List<StockAnalyticsData> metricas) {
        if (metricas.isEmpty()) return List.of();

        // ── 1. PLANNER ──────────────────────────────────────────────────────────
        log.info("Orchestrator: iniciando pipeline multi-agente para {} productos", metricas.size());
        List<AgentTask> tasks = plannerAgent.planificar(metricas);

        if (tasks.isEmpty()) {
            log.info("Orchestrator: PlannerAgent no generó tareas — inventario sin alertas");
            return List.of();
        }

        log.info("Orchestrator: PlannerAgent generó {} tareas", tasks.size());

        // ── 2. EXECUTOR + CHECKER en paralelo (tareas independientes entre sí) ──
        List<CompletableFuture<AgentTaskResult>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(
                        () -> ejecutarConVerificacion(task),
                        taskExecutor))
                .toList();

        // ── 3. Consolidar resultados ─────────────────────────────────────────────
        List<AgentTaskResult> results = new ArrayList<>();
        for (CompletableFuture<AgentTaskResult> future : futures) {
            try {
                results.add(future.join());
            } catch (Exception e) {
                log.error("Orchestrator: error obteniendo resultado de future: {}", e.getMessage());
            }
        }

        long aprobados   = results.stream().filter(r -> !r.isBajaPrecision()).count();
        long bajaPrecision = results.stream().filter(AgentTaskResult::isBajaPrecision).count();

        log.info("Orchestrator: pipeline completado — {} aprobados, {} baja precisión",
                aprobados, bajaPrecision);

        return results;
    }

    /**
     * Loop Executor → Checker para UNA tarea indivisible.
     * Reintenta hasta maxRetries veces pasando el feedback del Checker al Executor.
     */
    private AgentTaskResult ejecutarConVerificacion(AgentTask task) {
        log.debug("Orchestrator [{}]: iniciando loop execute-check", task.sku());

        String feedbackAcumulado = null;
        AgentTaskResult ultimoResultado = null;

        for (int intento = 1; intento <= maxRetries; intento++) {
            log.debug("Orchestrator [{}]: intento {}/{}", task.sku(), intento, maxRetries);

            // ── EXECUTOR ──────────────────────────────────────────────────────────
            AgentTaskResult resultado = executorAgent.ejecutar(task, feedbackAcumulado);
            resultado.setIntentos(intento);
            ultimoResultado = resultado;

            // ── CHECKER ───────────────────────────────────────────────────────────
            CheckResult check = checkerAgent.verificar(task, resultado);

            if (check.isValido()) {
                log.debug("Orchestrator [{}]: APROBADO en intento {}", task.sku(), intento);
                return resultado;
            }

            // Acumular feedback para el siguiente intento
            log.info("Orchestrator [{}]: RECHAZADO en intento {} — {}",
                    task.sku(), intento, check.getFeedback());

            feedbackAcumulado = buildFeedbackAcumulado(feedbackAcumulado, intento, check);
        }

        // Agotados los reintentos: usar el último resultado marcado como baja precisión
        log.warn("Orchestrator [{}]: agotados {} reintentos, usando último resultado con bajaPrecision=true",
                task.sku(), maxRetries);

        if (ultimoResultado != null) {
            ultimoResultado.setBajaPrecision(true);
            return ultimoResultado;
        }

        // Caso extremo: ni siquiera se obtuvo resultado
        return buildResultadoVacio(task);
    }

    private String buildFeedbackAcumulado(String feedbackPrevio, int intento, CheckResult check) {
        String nuevoFeedback = String.format(
                "Intento %d rechazado. Problema: %s. Campos a corregir: %s.",
                intento,
                check.getFeedback(),
                check.getCamposCriticos() != null ? String.join(", ", check.getCamposCriticos()) : "ver feedback"
        );

        if (feedbackPrevio != null && !feedbackPrevio.isBlank()) {
            return feedbackPrevio + "\n" + nuevoFeedback;
        }
        return nuevoFeedback;
    }

    private AgentTaskResult buildResultadoVacio(AgentTask task) {
        AgentTaskResult r = new AgentTaskResult();
        r.setSku(task.sku());
        r.setNivelRiesgo(task.metricas().nivelRiesgo().name());
        r.setDiasRestantesEstimados(task.metricas().diasDeStock());
        r.setCantidadReponerSugerida(0);
        r.setObservacion("No se pudo obtener análisis de IA después de " + maxRetries + " intentos.");
        r.setRequiereNotificacion(task.metricas().nivelRiesgo() != StockAnalyticsData.RiskLevel.OK);
        r.setTipoAlerta("STOCK_BAJO");
        r.setIntentos(maxRetries);
        r.setBajaPrecision(true);
        return r;
    }
}
