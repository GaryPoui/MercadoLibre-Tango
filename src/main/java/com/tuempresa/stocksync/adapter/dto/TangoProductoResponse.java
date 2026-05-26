package com.tuempresa.stocksync.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TangoProductoResponse {

    @JsonProperty("IdProducto")
    private String idProducto;

    @JsonProperty("Codigo")
    private String codigo;

    @JsonProperty("Descripcion")
    private String descripcion;

    @JsonProperty("StockActual")
    private Integer stockActual;

    @JsonProperty("Precio")
    private BigDecimal precio;

    @JsonProperty("Activo")
    private Boolean activo;
}
