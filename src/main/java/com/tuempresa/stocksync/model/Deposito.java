package com.tuempresa.stocksync.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "depositos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deposito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    private String direccion;

    // ID del depósito en Tango (para sincronización)
    @Column(name = "tango_deposito_id")
    private String tangoDepositoId;

    @Column(nullable = false)
    private boolean activo = true;

    // Si es true, este depósito se usa para calcular el stock disponible en ML
    @Column(name = "exporta_a_ml")
    private boolean exportaAML = true;
}
