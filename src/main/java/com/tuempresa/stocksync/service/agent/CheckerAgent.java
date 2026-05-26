package com.tuempresa.stocksync.service.agent;

import com.tuempresa.stocksync.model.dto.AgentTask;
import com.tuempresa.stocksync.model.dto.AgentTaskResult;
import com.tuempresa.stocksync.model.dto.CheckResult;
import com.tuempresa.stocksync.model.dto.StockAnalyticsData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * AGENTE 3 — VERIFICADOR
 *
 * Recibe la tarea original y el resultado del ExecutorAgent y verifica:
 * - Coherencia lógica (ej: nivelRiesgo vs diasRestantesEstimados)
 * - Coherencia con los datos de entrada (ej: cantidadSugerida vs ventasPorDia)
 * - Completitud (todos los campos requeridos presentes)
 *
 * Si detecta inconsistencias, devuelve valido=false con feedback concreto
 * para que el Orchestrator reintente con el ExecutorAgent.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CheckerAgent {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            Sos un verificador de calidad especializado en análisis de inventario.
            
            Vas a recibir:
            1. Los datos originales de un producto
            2. El análisis generado por otro agente
            
            Tu tarea es verificar si el análisis es COHERENTE y CORRECTO.
            
            Verificá específicamente:
            - nivelRiesgo coherente con diasRestantesEstimados:
              * AGOTADO: días <= 3 o stock = 0
              * CRITICO: días 4-7
              * BAJO: días 8-14
              * OK: días > 14
            - diasRestantesEstimados coherente con stock y ventasPorDia:
              * Si ventasPorDia > 0: debe ser aproximadamente stockActual / ventasPorDia
              * Si ventasPorDia = 0: debe ser 9999 (sin ventas recientes)
              * Tolerancia: ±20%% de diferencia es aceptable
            - cantidadReponerSugerida coherente:
              * Debe ser al menos ventasPorDia * 30 (cobertura mínima de 30 días)
              * Si es 0 pero requiereNotificacion=true → inconsistente
            - requiereNotificacion=true cuando nivelRiesgo es CRITICO o AGOTADO
            - tipoAlerta coherente con nivelRiesgo (AGOTADO→SIN_STOCK, CRITICO→AGOTAMIENTO_CERCANO, etc.)
            - sku en el resultado coincide con el sku de la tarea original
            
            Devolvé ÚNICAMENTE este JSON, sin texto adicional:
            {
              "valido": true|false,
              "feedback": "Descripción precisa de qué está mal y cómo corregirlo (vacío si valido=true)",
              "camposCriticos": ["campo1", "campo2"] // campos que deben corregirse (vacío si valido=true)
            }
            
            Sé estricto pero justo. No rechaces análisis por diferencias menores (< 20%%).
            """;

    /**
     * Verifica la coherencia del resultado del ExecutorAgent contra los datos originales.
     *
     * @param task   Tarea original con los datos reales del producto
     * @param result Resultado producido por el ExecutorAgent
     * @return CheckResult con veredicto y feedback
     */
    public CheckResult verificar(AgentTask task, AgentTaskResult result) {
        log.debug("CheckerAgent: verificando resultado para sku={}", task.sku());

        // Primero verificación determinista (sin IA) para casos obvios
        CheckResult quickCheck = verificacionRapida(task, result);
        if (!quickCheck.isValido()) {
            log.info("CheckerAgent: rechazo rápido para sku={}: {}", task.sku(), quickCheck.getFeedback());
            return quickCheck;
        }

        // Verificación profunda con IA para casos sutiles
        try {
            String userMessage = buildVerificationMessage(task, result);

            CheckResult aiCheck = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userMessage)
                    .call()
                    .entity(CheckResult.class);

            if (aiCheck == null) {
                log.warn("CheckerAgent: respuesta nula para sku={}, aprobando por defecto", task.sku());
                return passResult();
            }

            if (aiCheck.isValido()) {
                log.debug("CheckerAgent: sku={} APROBADO", task.sku());
            } else {
                log.info("CheckerAgent: sku={} RECHAZADO — {}", task.sku(), aiCheck.getFeedback());
            }

            return aiCheck;

        } catch (Exception e) {
            log.error("CheckerAgent: error verificando sku={}: {}", task.sku(), e.getMessage());
            // En caso de error del checker, aprobar para no bloquear el flujo
            return passResult();
        }
    }

    /**
     * Verificación determinista sin IA — detecta inconsistencias matemáticas obvias.
     * Es más rápida y más confiable que la IA para validaciones numéricas.
     */
    private CheckResult verificacionRapida(AgentTask task, AgentTaskResult result) {
        StockAnalyticsData m = task.metricas();

        // SKU incorrecto
        if (!task.sku().equals(result.getSku())) {
            return failResult(
                    "El SKU del resultado (" + result.getSku() + ") no coincide con la tarea (" + task.sku() + ")",
                    List.of("sku")
            );
        }

        // Stock agotado pero nivelRiesgo no es AGOTADO
        if (m.stockActual() == 0 && !"AGOTADO".equals(result.getNivelRiesgo())) {
            return failResult(
                    "Stock actual es 0 pero nivelRiesgo es '" + result.getNivelRiesgo() + "'. Debe ser AGOTADO.",
                    List.of("nivelRiesgo")
            );
        }

        // Días vs nivelRiesgo inconsistentes
        int dias = result.getDiasRestantesEstimados();
        String riesgo = result.getNivelRiesgo();
        boolean diasRiesgoCoherente = switch (riesgo) {
            case "AGOTADO" -> dias <= 3 || m.stockActual() == 0;
            case "CRITICO" -> dias >= 4 && dias <= 7;
            case "BAJO"    -> dias >= 8 && dias <= 14;
            case "OK"      -> dias > 14 || m.ventasPorDia() == 0;
            default        -> true;
        };

        if (!diasRiesgoCoherente) {
            return failResult(
                    "nivelRiesgo='" + riesgo + "' es inconsistente con diasRestantesEstimados=" + dias
                    + ". Revisá la tabla de rangos.",
                    List.of("nivelRiesgo", "diasRestantesEstimados")
            );
        }

        // requiereNotificacion debe ser true para CRITICO y AGOTADO
        if (("CRITICO".equals(riesgo) || "AGOTADO".equals(riesgo)) && !result.isRequiereNotificacion()) {
            return failResult(
                    "requiereNotificacion debe ser true cuando nivelRiesgo es " + riesgo,
                    List.of("requiereNotificacion")
            );
        }

        // cantidadReponerSugerida demasiado baja
        if (m.ventasPorDia() > 0) {
            int minimoEsperado = (int) Math.ceil(m.ventasPorDia() * 30 * 0.5); // tolerancia 50%
            if (result.getCantidadReponerSugerida() < minimoEsperado && result.isRequiereNotificacion()) {
                return failResult(
                        "cantidadReponerSugerida=" + result.getCantidadReponerSugerida()
                        + " parece muy baja. Con " + m.ventasPorDia() + " ventas/día, lo mínimo sería ~"
                        + minimoEsperado + " unidades (15 días de cobertura).",
                        List.of("cantidadReponerSugerida")
                );
            }
        }

        return passResult();
    }

    private String buildVerificationMessage(AgentTask task, AgentTaskResult result) {
        StockAnalyticsData m = task.metricas();
        return """
                DATOS ORIGINALES DEL PRODUCTO:
                SKU: %s
                Stock actual: %d
                Ventas/día (30d): %.2f
                Total vendido (30d): %d
                Días de cobertura calculados: %d
                
                ANÁLISIS GENERADO:
                nivelRiesgo: %s
                diasRestantesEstimados: %d
                cantidadReponerSugerida: %d
                requiereNotificacion: %s
                tipoAlerta: %s
                observacion: %s
                
                Verificá si este análisis es coherente con los datos originales.
                """.formatted(
                m.sku(), m.stockActual(), m.ventasPorDia(),
                m.totalVendidoUltimos30Dias(), m.diasDeStock(),
                result.getNivelRiesgo(), result.getDiasRestantesEstimados(),
                result.getCantidadReponerSugerida(), result.isRequiereNotificacion(),
                result.getTipoAlerta(), result.getObservacion()
        );
    }

    private CheckResult passResult() {
        CheckResult r = new CheckResult();
        r.setValido(true);
        r.setFeedback("");
        r.setCamposCriticos(List.of());
        return r;
    }

    private CheckResult failResult(String feedback, java.util.List<String> campos) {
        CheckResult r = new CheckResult();
        r.setValido(false);
        r.setFeedback(feedback);
        r.setCamposCriticos(campos);
        return r;
    }
}
