package com.tuempresa.stocksync.service;

import com.tuempresa.stocksync.adapter.MLClient;
import com.tuempresa.stocksync.adapter.dto.MLOrderResponse;
import com.tuempresa.stocksync.adapter.dto.MLWebhookPayload;
import com.tuempresa.stocksync.model.SyncEvent;
import com.tuempresa.stocksync.model.SyncLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MLWebhookService {

    private final ApplicationEventPublisher eventPublisher;
    private final MLClient mlClient;
    private final MLWebhookValidator webhookValidator;

    /**
     * Procesa un webhook recibido de MercadoLibre.
     * Responde rápido y delega el procesamiento real al event bus.
     */
    public void processWebhook(MLWebhookPayload payload, String rawBody, String signature) {
        if (!webhookValidator.isValid(signature, rawBody)) {
            log.warn("Webhook con firma inválida recibido");
            throw new SecurityException("Firma de webhook inválida");
        }

        log.info("Webhook ML recibido: topic={} resource={}", payload.getTopic(), payload.getResource());

        // Solo procesar eventos de órdenes pagadas
        if (!"orders_v2".equals(payload.getTopic())) {
            log.debug("Topic {} ignorado", payload.getTopic());
            return;
        }

        String orderId = payload.extractResourceId();
        if (orderId == null) {
            log.warn("No se pudo extraer orderId del webhook");
            return;
        }

        // Obtener detalles de la orden de forma asíncrona
        try {
            MLOrderResponse order = mlClient.getOrder(orderId);

            if (order == null || order.getOrderItems() == null) {
                log.warn("Orden {} sin ítems o nula", orderId);
                return;
            }

            // Verificar que la orden esté pagada
            if (!"paid".equals(order.getStatus())) {
                log.debug("Orden {} con estado {} ignorada", orderId, order.getStatus());
                return;
            }

            // Publicar un evento por cada ítem de la orden
            for (MLOrderResponse.MLOrderItem orderItem : order.getOrderItems()) {
                String sku = orderItem.getItem().getSellerSku();
                String mlItemId = orderItem.getItem().getId();

                SyncEvent event = SyncEvent.builder()
                        .source(this)
                        .sku(sku)
                        .mlItemId(mlItemId)
                        .quantity(orderItem.getQuantity())
                        .origen(SyncLog.SyncOrigin.MERCADOLIBRE)
                        .orderId(orderId)
                        .build();

                log.info("Publicando SyncEvent: sku={} qty={} orderId={}", sku, orderItem.getQuantity(), orderId);
                eventPublisher.publishEvent(event);
            }

        } catch (Exception e) {
            log.error("Error procesando webhook orderId={}: {}", orderId, e.getMessage(), e);
        }
    }
}
