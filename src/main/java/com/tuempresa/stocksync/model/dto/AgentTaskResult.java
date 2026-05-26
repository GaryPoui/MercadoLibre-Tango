package com.tuempresa.stocksync.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Resultado producido por el ExecutorAgent para una tarea indivisible.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentTaskResult {

    private String sku;
    private String nivelRiesgo;              // OK, BAJO, CRITICO, AGOTADO
    private int diasRestantesEstimados;
    private int cantidadReponerSugerida;
    private String observacion;              // Explicación detallada del análisis
    private boolean requiereNotificacion;
    private String tipoAlerta;              // Valor de StockAlert.AlertType

    // Metadatos de ejecución — no vienen de la IA, los asigna el Orchestrator
    private int intentos = 1;
    private boolean bajaPrecision = false;  // true si agotó reintentos sin pasar el Checker
}
