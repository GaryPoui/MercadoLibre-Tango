package com.tuempresa.stocksync.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Un Kit es un producto compuesto por múltiples componentes.
 * Cuando se vende un kit en ML, el sistema descuenta automáticamente
 * el stock de cada componente individual.
 * El kit en sí no tiene stock físico — es virtual.
 */
@Entity
@Table(name = "kits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Kit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SKU del kit — debe existir como publicación en ML
    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;

    // ID del ítem en MercadoLibre
    @Column(name = "ml_item_id")
    private String mlItemId;

    @Column(nullable = false)
    private boolean activo = true;

    @OneToMany(mappedBy = "kit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<KitComponente> componentes = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Calcula el stock disponible del kit basado en el componente más escaso.
     * Un kit solo puede armarse si TODOS sus componentes tienen stock.
     */
    public int calcularStockDisponible() {
        if (componentes.isEmpty()) return 0;
        return componentes.stream()
                .mapToInt(c -> {
                    int stockComponente = c.getStockItem().getStock();
                    return stockComponente / c.getCantidad();
                })
                .min()
                .orElse(0);
    }
}
