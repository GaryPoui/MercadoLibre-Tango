package com.tuempresa.stocksync.adapter;

import com.tuempresa.stocksync.adapter.dto.TangoProductoResponse;
import com.tuempresa.stocksync.config.TangoConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class TangoClient {

    private final RestClient restClient;
    private final TangoConfig tangoConfig;

    /**
     * Obtiene todos los productos con su stock actual desde Tango Nexo.
     */
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public List<TangoProductoResponse> getProductos() {
        log.debug("Obteniendo productos desde Tango");
        return restClient.get()
                .uri(tangoConfig.getBaseUrl() + "/api/v1/productos")
                .header("X-Api-Key", tangoConfig.getApiKey())
                .header("X-Empresa", tangoConfig.getEmpresa())
                .retrieve()
                .body(new ParameterizedTypeReference<List<TangoProductoResponse>>() {});
    }

    /**
     * Obtiene un producto específico por su código/SKU.
     */
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public TangoProductoResponse getProducto(String codigoProducto) {
        log.debug("Obteniendo producto Tango: {}", codigoProducto);
        return restClient.get()
                .uri(tangoConfig.getBaseUrl() + "/api/v1/productos/{codigo}", codigoProducto)
                .header("X-Api-Key", tangoConfig.getApiKey())
                .header("X-Empresa", tangoConfig.getEmpresa())
                .retrieve()
                .body(TangoProductoResponse.class);
    }

    /**
     * Actualiza el stock de un producto en Tango.
     * Tango Nexo utiliza movimientos de stock para actualizar cantidades.
     */
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void updateStock(String codigoProducto, int nuevoStock) {
        log.info("Actualizando stock Tango: sku={} nuevoStock={}", codigoProducto, nuevoStock);

        Map<String, Object> body = Map.of(
                "Codigo", codigoProducto,
                "StockNuevo", nuevoStock,
                "TipoMovimiento", "AJUSTE",
                "Observacion", "Sincronización automática desde MercadoLibre"
        );

        restClient.post()
                .uri(tangoConfig.getBaseUrl() + "/api/v1/stock/ajuste")
                .header("X-Api-Key", tangoConfig.getApiKey())
                .header("X-Empresa", tangoConfig.getEmpresa())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Actualiza el precio de un producto en Tango.
     */
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void updatePrice(String codigoProducto, BigDecimal nuevoPrecio) {
        log.info("Actualizando precio Tango: sku={} nuevoPrecio={}", codigoProducto, nuevoPrecio);
        Map<String, Object> body = Map.of(
                "Codigo", codigoProducto,
                "Precio", nuevoPrecio,
                "ListaPrecio", "1"
        );
        restClient.post()
                .uri(tangoConfig.getBaseUrl() + "/api/v1/precios/actualizar")
                .header("X-Api-Key", tangoConfig.getApiKey())
                .header("X-Empresa", tangoConfig.getEmpresa())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Obtiene productos actualizados desde la última sincronización.
     * Útil para el polling periódico Tango → ML.
     */
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public List<TangoProductoResponse> getProductosActualizados() {
        log.debug("Obteniendo productos actualizados desde Tango");
        return restClient.get()
                .uri(tangoConfig.getBaseUrl() + "/api/v1/productos?modificados=true")
                .header("X-Api-Key", tangoConfig.getApiKey())
                .header("X-Empresa", tangoConfig.getEmpresa())
                .retrieve()
                .body(new ParameterizedTypeReference<List<TangoProductoResponse>>() {});
    }
}
