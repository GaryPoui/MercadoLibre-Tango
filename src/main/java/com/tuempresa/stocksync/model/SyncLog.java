package com.tuempresa.stocksync.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sync_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SyncOrigin origen;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SyncStatus estado;

    @Column(name = "stock_anterior")
    private Integer stockAnterior;

    @Column(name = "stock_nuevo")
    private Integer stockNuevo;

    @Column(name = "mensaje_error", columnDefinition = "TEXT")
    private String mensajeError;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum SyncOrigin {
        MERCADOLIBRE, TANGO, SHEETS, MANUAL
    }

    public enum SyncStatus {
        OK, ERROR, PARCIAL
    }
}
