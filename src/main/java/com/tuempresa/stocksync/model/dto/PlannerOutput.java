package com.tuempresa.stocksync.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Respuesta estructurada del PlannerAgent:
 * lista de tareas a ejecutar y estrategia general.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlannerOutput {

    private List<PlannedTask> tareas;
    private String estrategiaGeneral; // Resumen de qué decidió hacer y por qué

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlannedTask {
        private String sku;
        private String prioridad;       // ALTA, MEDIA, BAJA
        private String razonAnalisis;
        private String contextExtra;
    }
}
