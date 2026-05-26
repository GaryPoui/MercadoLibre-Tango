package com.tuempresa.stocksync.model.dto;

/**
 * Tarea indivisible creada por el PlannerAgent para que el ExecutorAgent la procese.
 * Cada tarea representa el análisis profundo de UN solo producto.
 */
public record AgentTask(
        String sku,
        String nombre,
        StockAnalyticsData metricas,
        String prioridad,       // ALTA, MEDIA, BAJA — asignada por el planner
        String razonAnalisis,   // Por qué el planner eligió este producto
        String contextExtra     // Contexto adicional que el planner considera relevante
) {}
