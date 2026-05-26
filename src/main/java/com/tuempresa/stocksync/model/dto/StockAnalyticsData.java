package com.tuempresa.stocksync.model.dto;

/**
 * Métricas calculadas de un producto para enviárselas a la IA.
 * Contiene historial de ventas, velocidad y proyección.
 */
public record StockAnalyticsData(
        String sku,
        String nombre,
        int stockActual,
        int stockMinimo,
        double ventasPorDia,       // promedio de unidades vendidas por día (últimos N días)
        int diasDeStock,           // stockActual / ventasPorDia (estimado)
        int totalVendidoUltimos30Dias,
        RiskLevel nivelRiesgo
) {
    public enum RiskLevel {
        OK,       // > 14 días de cobertura
        BAJO,     // 7-14 días
        CRITICO,  // 3-7 días
        AGOTADO   // 0-3 días o stock = 0
    }
}
