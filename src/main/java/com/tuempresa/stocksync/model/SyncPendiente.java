package com.tuempresa.stocksync.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tabla outbox: registra operaciones de sincronización pendientes.
 * Si un sync externo falla (ML, Tango, Sheets), queda aquí para reintento automático.
 */
@Entity
@Table(name = "sync_pendientes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncPendiente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DestinoSync destino;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TipoOperacion tipoOperacion;

    // Valor a sincronizar (stock o precio serializado)
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EstadoSync estado = EstadoSync.PENDIENTE;

    @Column(name = "intentos", nullable = false)
    @Builder.Default
    private int intentos = 0;

    @Column(name = "max_intentos", nullable = false)
    @Builder.Default
    private int maxIntentos = 5;

    @Column(name = "ultimo_error", columnDefinition = "TEXT")
    private String ultimoError;

    @Column(name = "proximo_intento")
    private LocalDateTime proximoIntento;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (proximoIntento == null) proximoIntento = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum DestinoSync {
        MERCADOLIBRE, TANGO, SHEETS
    }

    public enum TipoOperacion {
        UPDATE_STOCK, UPDATE_PRICE
    }

    public enum EstadoSync {
        PENDIENTE, EN_PROCESO, COMPLETADO, FALLIDO_PERMANENTE
    }
}
