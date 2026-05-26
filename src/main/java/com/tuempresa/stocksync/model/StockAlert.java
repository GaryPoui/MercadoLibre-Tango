package com.tuempresa.stocksync.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertType tipo;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertStatus estado;

    @Column(name = "stock_actual")
    private Integer stockActual;

    @Column(name = "dias_restantes")
    private Integer diasRestantes;

    @Column(name = "cantidad_sugerida")
    private Integer cantidadSugerida;

    @Column(columnDefinition = "TEXT")
    private String mensaje;

    // Observación detallada generada por la IA
    @Column(name = "observacion_ia", columnDefinition = "TEXT")
    private String observacionIA;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        estado = AlertStatus.PENDIENTE;
    }

    public enum AlertType {
        STOCK_BAJO,          // Stock por debajo del mínimo configurado
        AGOTAMIENTO_CERCANO, // La IA predice agotamiento en X días
        SIN_STOCK,           // Stock llegó a 0
        ANOMALIA_VENTAS,     // Caída o pico inusual detectado por IA
        REPOSICION_SUGERIDA  // Sugerencia proactiva de reposición
    }

    public enum AlertStatus {
        PENDIENTE,   // Generada, sin notificar
        NOTIFICADA,  // Notificación enviada
        RESUELTA,    // El stock fue repuesto
        IGNORADA     // Marcada como ignorada manualmente
    }
}
