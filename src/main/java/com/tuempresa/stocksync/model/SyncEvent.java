package com.tuempresa.stocksync.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Evento interno publicado por ApplicationEventPublisher
// cuando llega un webhook de MercadoLibre
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncEvent {

    private Object source;
    private String sku;
    private String mlItemId;
    private int quantity;   // cantidad vendida
    private SyncLog.SyncOrigin origen;
    private String orderId;
}
