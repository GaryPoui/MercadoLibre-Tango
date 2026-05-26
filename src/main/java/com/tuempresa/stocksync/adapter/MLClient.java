package com.tuempresa.stocksync.adapter;

import com.tuempresa.stocksync.adapter.dto.MLItemResponse;
import com.tuempresa.stocksync.adapter.dto.MLOrderResponse;
import com.tuempresa.stocksync.config.MLConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class MLClient {

    private final RestClient restClient;
    private final MLConfig mlConfig;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    /**
     * Obtiene el stock actual de un ítem en MercadoLibre.
     */
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public MLItemResponse getItem(String mlItemId) {
        log.debug("Obteniendo ítem ML: {}", mlItemId);
        return restClient.get()
                .uri(mlConfig.getBaseUrl() + "/items/{id}", mlItemId)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(MLItemResponse.class);
    }

    /**
     * Actualiza el stock disponible de un ítem en MercadoLibre.
     */
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void updateStock(String mlItemId, int newStock) {
        log.info("Actualizando stock ML: itemId={} nuevoStock={}", mlItemId, newStock);
        restClient.put()
                .uri(mlConfig.getBaseUrl() + "/items/{id}", mlItemId)
                .header("Authorization", "Bearer " + getAccessToken())
                .body(Map.of("available_quantity", newStock))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Obtiene los detalles de una orden por su ID.
     */
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public MLOrderResponse getOrder(String orderId) {
        log.debug("Obteniendo orden ML: {}", orderId);
        return restClient.get()
                .uri(mlConfig.getBaseUrl() + "/orders/{id}", orderId)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(MLOrderResponse.class);
    }

    /**
     * Actualiza el precio de un ítem en MercadoLibre.
     */
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void updatePrice(String mlItemId, BigDecimal newPrice) {
        log.info("Actualizando precio ML: itemId={} nuevoPrecio={}", mlItemId, newPrice);
        restClient.put()
                .uri(mlConfig.getBaseUrl() + "/items/{id}", mlItemId)
                .header("Authorization", "Bearer " + getAccessToken())
                .body(Map.of("price", newPrice))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Obtiene el stock actual de un ítem directamente desde ML (fuente de verdad externa).
     */
    public int getCurrentStock(String mlItemId) {
        MLItemResponse item = getItem(mlItemId);
        return item != null ? item.getAvailableQuantity() : 0;
    }

    private String getAccessToken() {
        var authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId("mercadolibre")
                .principal("stocksync-app")
                .build();

        var client = authorizedClientManager.authorize(authorizeRequest);
        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("No se pudo obtener el token de acceso de MercadoLibre");
        }
        return client.getAccessToken().getTokenValue();
    }
}
