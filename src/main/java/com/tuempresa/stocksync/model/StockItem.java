package com.tuempresa.stocksync.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private Integer stock;

    @Column(name = "stock_minimo")
    private Integer stockMinimo;

    @Column(precision = 10, scale = 2)
    private BigDecimal precio;

    // ID del ítem en MercadoLibre (ej: MLA123456789)
    @Column(name = "ml_item_id")
    private String mlItemId;

    // ID del producto en Tango
    @Column(name = "tango_producto_id")
    private String tangoProductoId;

    // Fila en Google Sheets (para actualización directa)
    @Column(name = "sheets_row")
    private Integer sheetsRow;

    @Column(name = "ultima_sincronizacion")
    private LocalDateTime ultimaSincronizacion;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
