package com.tuempresa.stocksync.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Veredicto del CheckerAgent sobre el resultado del ExecutorAgent.
 * Si valido=false, el feedback se reenvía al ExecutorAgent para que corrija.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckResult {

    private boolean valido;
    private String feedback;              // Qué está mal y cómo corregirlo (para el retry)
    private List<String> camposCriticos;  // Campos específicos que son incoherentes
}
