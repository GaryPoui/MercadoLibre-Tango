package com.tuempresa.stocksync.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro de cada movimiento de stock (ingreso, egreso, ajuste, venta).
 * Es el historial completo de todas las operaciones de inventario.
 */
@Entity
@Table(name = "movimientos_stock")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TipoMovimiento tipo;

    // Cantidad del movimiento (siempre positiva; el tipo determina si suma o resta)
    @Column(nullable = false)
    private int cantidad;

    @Column(name = "stock_antes", nullable = false)
    private int stockAntes;

    @Column(name = "stock_despues", nullable = false)
    private int stockDespues;

    // Depósito afectado (null = depósito general)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposito_id")
    private Deposito deposito;

    private String motivo;

    // Número de orden ML, remito, factura, etc.
    @Column(name = "referencia_externa")
    private String referenciaExterna;

    // Usuario o sistema que generó el movimiento
    @Column(name = "origen_sistema")
    private String origenSistema;

    @Column(name = "fecha_movimiento", nullable = false)
    private LocalDateTime fechaMovimiento;

    @PrePersist
    protected void onCreate() {
        if (fechaMovimiento == null) fechaMovimiento = LocalDateTime.now();
    }

    public enum TipoMovimiento {
        INGRESO_COMPRA,       // Mercadería comprada a proveedor
        INGRESO_DEVOLUCION,   // Cliente devuelve mercadería
        INGRESO_TRANSFERENCIA,// Transferencia desde otro depósito
        EGRESO_VENTA,         // Venta en MercadoLibre (automático)
        EGRESO_MERMA,         // Pérdida, rotura, vencimiento
        EGRESO_TRANSFERENCIA, // Transferencia hacia otro depósito
        AJUSTE_POSITIVO,      // Ajuste de inventario (surplus)
        AJUSTE_NEGATIVO       // Ajuste de inventario (faltante)
    }
}
