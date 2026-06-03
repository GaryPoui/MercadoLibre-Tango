package com.tuempresa.stocksync.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Historial de cambios de precio para auditoría.
 * Se registra un entry cada vez que se detecta un cambio de precio.
 */
@Entity
@Table(name = "historial_precios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialPrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sku;

    @Column(name = "precio_anterior", precision = 10, scale = 2)
    private BigDecimal precioAnterior;

    @Column(name = "precio_nuevo", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioNuevo;

    @Column(name = "variacion_porcentual", precision = 5, scale = 2)
    private BigDecimal variacionPorcentual;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrigenCambio origen;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (precioAnterior != null && precioAnterior.compareTo(BigDecimal.ZERO) > 0) {
            variacionPorcentual = precioNuevo.subtract(precioAnterior)
                    .divide(precioAnterior, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
    }

    public enum OrigenCambio {
        SHEETS, MANUAL, TANGO, MERCADOLIBRE
    }
}
