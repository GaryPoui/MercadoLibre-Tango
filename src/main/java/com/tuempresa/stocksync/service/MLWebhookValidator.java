package com.tuempresa.stocksync.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class MLWebhookValidator {

    @Value("${mercadolibre.webhook-secret}")
    private String webhookSecret;

    /**
     * Valida la firma HMAC-SHA256 del webhook de MercadoLibre.
     * ML envía en el header x-signature: ts=TIMESTAMP,v1=HASH
     */
    public boolean isValid(String signatureHeader, String rawBody) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }

        try {
            // Parsear header: "ts=TIMESTAMP,v1=HASH"
            String ts = null;
            String v1 = null;
            for (String part : signatureHeader.split(",")) {
                if (part.startsWith("ts=")) ts = part.substring(3);
                if (part.startsWith("v1=")) v1 = part.substring(3);
            }

            if (ts == null || v1 == null) return false;

            // Mensaje a verificar: "ts:TIMESTAMP.v1:BODY"
            String message = "ts:" + ts + ".v1:" + rawBody;

            // Calcular HMAC-SHA256
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(keySpec);
            byte[] hash = hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            String computedHash = HexFormat.of().formatHex(hash);

            // Comparación en tiempo constante para evitar timing attacks
            return MessageDigest.isEqual(
                    computedHash.getBytes(StandardCharsets.UTF_8),
                    v1.getBytes(StandardCharsets.UTF_8)
            );

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }
}
