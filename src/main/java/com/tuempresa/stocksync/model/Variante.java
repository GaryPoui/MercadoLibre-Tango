package com.tuempresa.stocksync.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Variante de un producto (ej: "Remera talle M color rojo").
 * Cada variante tiene su propio SKU, stock y atributos.
 * El StockItem padre representa el producto base (ej: "Remera").
 */
@Entity
@Table(name = "variantes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Variante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SKU único de esta variante
    @Column(nullable = false, unique = true)
    private String sku;

    // Producto base al que pertenece esta variante
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_item_padre_id", nullable = false)
    private StockItem stockItemPadre;

    // ID de la variante en MercadoLibre (dentro del ítem padre)
    @Column(name = "ml_variante_id")
    private String mlVarianteId;

    // Atributos de la variante: {"COLOR": "Rojo", "TALLE": "M"}
    @ElementCollection
    @CollectionTable(name = "variante_atributos",
            joinColumns = @JoinColumn(name = "variante_id"))
    @MapKeyColumn(name = "atributo")
    @Column(name = "valor")
    @Builder.Default
    private Map<String, String> atributos = new HashMap<>();

    @Column(nullable = false)
    private int stock;

    // Precio diferenciado (null = usa el precio del padre)
    @Column(precision = 10, scale = 2)
    private BigDecimal precioEspecial;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    @PrePersist
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
