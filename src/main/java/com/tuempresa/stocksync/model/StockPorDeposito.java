package com.tuempresa.stocksync.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_por_deposito",
       uniqueConstraints = @UniqueConstraint(columnNames = {"stock_item_id", "deposito_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPorDeposito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposito_id", nullable = false)
    private Deposito deposito;

    @Column(nullable = false)
    private int stock;

    @Column(name = "stock_reservado")
    private int stockReservado = 0; // unidades comprometidas pero no despachadas

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    @PrePersist
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public int getStockDisponible() {
        return Math.max(0, stock - stockReservado);
    }
}
