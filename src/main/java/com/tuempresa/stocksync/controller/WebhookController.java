package com.tuempresa.stocksync.controller;

import com.tuempresa.stocksync.adapter.dto.MLWebhookPayload;
import com.tuempresa.stocksync.service.MLWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
@Slf4j
@RequiredArgsConstructor
public class WebhookController {

    private final MLWebhookService webhookService;

    /**
     * Endpoint que recibe notificaciones de MercadoLibre.
     * ML exige respuesta HTTP 200 en menos de 500ms.
     * El procesamiento real se delega al event bus asíncrono.
     */
    @PostMapping("/ml")
    public ResponseEntity<Void> handleMLWebhook(
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestBody String rawBody) {

        log.debug("Webhook recibido desde ML");

        try {
            MLWebhookPayload payload = parsePayload(rawBody);
            webhookService.processWebhook(payload, rawBody, signature);
        } catch (SecurityException e) {
            log.warn("Webhook rechazado: firma inválida");
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Error procesando webhook: {}", e.getMessage(), e);
            // Retornar 200 igual para que ML no reintente indefinidamente
            // El error ya fue logueado y se manejará internamente
        }

        return ResponseEntity.ok().build();
    }

    private MLWebhookPayload parsePayload(String rawBody) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(rawBody, MLWebhookPayload.class);
        } catch (Exception e) {
            log.error("Error parseando payload del webhook: {}", e.getMessage());
            throw new RuntimeException("Payload inválido", e);
        }
    }
}
