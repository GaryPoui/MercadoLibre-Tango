package com.tuempresa.stocksync.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Respuesta estructurada que devuelve la IA tras analizar el stock.
 * Spring AI convierte automáticamente el JSON del modelo a esta clase.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AIAnalysisResult {

    private List<ItemAnalysis> items;
    private String resumenGeneral;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemAnalysis {
        private String sku;
        private String nivelRiesgo;           // OK, BAJO, CRITICO, AGOTADO
        private int diasRestantesEstimados;
        private int cantidadReponerSugerida;
        private String observacion;            // Explicación del análisis
        private boolean requiereNotificacion;
        private String tipoAlerta;            // Tipo de StockAlert.AlertType a generar
    }
}
