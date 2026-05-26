package com.tuempresa.stocksync.service;

import com.tuempresa.stocksync.model.StockAlert;
import com.tuempresa.stocksync.repository.StockAlertRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final StockAlertRepository alertRepository;
    private final RestClient restClient;

    @Value("${notification.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${notification.email.destinatario:}")
    private String destinatarioEmail;

    @Value("${notification.webhook.enabled:false}")
    private boolean webhookEnabled;

    @Value("${notification.webhook.url:}")
    private String webhookUrl;

    /**
     * Punto de entrada principal: notifica por todos los canales configurados
     * y marca la alerta como notificada.
     */
    public void notificar(StockAlert alerta) {
        boolean enviado = false;

        if (emailEnabled && destinatarioEmail != null && !destinatarioEmail.isBlank()) {
            enviado |= enviarEmail(alerta);
        }

        if (webhookEnabled && webhookUrl != null && !webhookUrl.isBlank()) {
            enviado |= enviarWebhook(alerta);
        }

        if (enviado) {
            alerta.setEstado(StockAlert.AlertStatus.NOTIFICADA);
            alerta.setNotifiedAt(LocalDateTime.now());
            alertRepository.save(alerta);
        }
    }

    // ─── Email ───────────────────────────────────────────────────────────────────

    private boolean enviarEmail(StockAlert alerta) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(destinatarioEmail);
            helper.setSubject(buildEmailSubject(alerta));
            helper.setText(buildEmailBody(alerta), true); // true = HTML

            mailSender.send(message);
            log.info("Email de alerta enviado: sku={} tipo={}", alerta.getSku(), alerta.getTipo());
            return true;

        } catch (Exception e) {
            log.error("Error enviando email para alerta sku={}: {}", alerta.getSku(), e.getMessage());
            return false;
        }
    }

    private String buildEmailSubject(StockAlert alerta) {
        return switch (alerta.getTipo()) {
            case SIN_STOCK         -> "🔴 SIN STOCK: " + alerta.getNombre();
            case AGOTAMIENTO_CERCANO -> "🟠 STOCK CRÍTICO: " + alerta.getNombre();
            case STOCK_BAJO        -> "🟡 Stock bajo: " + alerta.getNombre();
            case ANOMALIA_VENTAS   -> "⚠️ Anomalía detectada: " + alerta.getNombre();
            case REPOSICION_SUGERIDA -> "📦 Sugerencia de reposición: " + alerta.getNombre();
        };
    }

    private String buildEmailBody(StockAlert alerta) {
        String colorBadge = switch (alerta.getTipo()) {
            case SIN_STOCK, AGOTAMIENTO_CERCANO -> "#dc3545";
            case STOCK_BAJO                     -> "#fd7e14";
            case ANOMALIA_VENTAS                -> "#ffc107";
            case REPOSICION_SUGERIDA            -> "#0d6efd";
        };

        String diasTexto = alerta.getDiasRestantes() != null && alerta.getDiasRestantes() < 9999
                ? alerta.getDiasRestantes() + " días estimados"
                : "Sin ventas recientes";

        String reposicionTexto = alerta.getCantidadSugerida() != null
                ? alerta.getCantidadSugerida() + " unidades"
                : "—";

        String observacion = alerta.getObservacionIA() != null
                ? "<p><strong>Análisis IA:</strong> " + alerta.getObservacionIA() + "</p>"
                : "";

        return """
                <html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background-color: %s; color: white; padding: 16px; border-radius: 8px 8px 0 0;">
                        <h2 style="margin: 0;">⚠️ Alerta de Stock — %s</h2>
                    </div>
                    <div style="border: 1px solid #ddd; border-top: none; padding: 20px; border-radius: 0 0 8px 8px;">
                        <table style="width: 100%%; border-collapse: collapse;">
                            <tr><td style="padding: 8px; color: #666;">Producto</td>
                                <td style="padding: 8px; font-weight: bold;">%s</td></tr>
                            <tr style="background: #f8f9fa;"><td style="padding: 8px; color: #666;">SKU</td>
                                <td style="padding: 8px;">%s</td></tr>
                            <tr><td style="padding: 8px; color: #666;">Stock actual</td>
                                <td style="padding: 8px; font-weight: bold; color: %s;">%d unidades</td></tr>
                            <tr style="background: #f8f9fa;"><td style="padding: 8px; color: #666;">Cobertura estimada</td>
                                <td style="padding: 8px;">%s</td></tr>
                            <tr><td style="padding: 8px; color: #666;">Reposición sugerida</td>
                                <td style="padding: 8px;">%s</td></tr>
                        </table>
                        %s
                        <hr style="margin: 16px 0; border: none; border-top: 1px solid #ddd;">
                        <p style="color: #999; font-size: 12px;">
                            Generado automáticamente por StockSync el %s
                        </p>
                    </div>
                </html></body>
                """.formatted(
                colorBadge,
                alerta.getTipo().name().replace("_", " "),
                alerta.getNombre(),
                alerta.getSku(),
                colorBadge,
                alerta.getStockActual(),
                diasTexto,
                reposicionTexto,
                observacion,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        );
    }

    // ─── Webhook genérico (Slack, Discord, etc.) ─────────────────────────────────

    private boolean enviarWebhook(StockAlert alerta) {
        try {
            Map<String, Object> payload = Map.of(
                    "text", buildWebhookText(alerta),
                    "sku", alerta.getSku(),
                    "nombre", alerta.getNombre(),
                    "tipo", alerta.getTipo().name(),
                    "stockActual", alerta.getStockActual(),
                    "diasRestantes", alerta.getDiasRestantes() != null ? alerta.getDiasRestantes() : -1,
                    "cantidadSugerida", alerta.getCantidadSugerida() != null ? alerta.getCantidadSugerida() : 0,
                    "timestamp", LocalDateTime.now().toString()
            );

            restClient.post()
                    .uri(webhookUrl)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Webhook de alerta enviado: sku={}", alerta.getSku());
            return true;

        } catch (Exception e) {
            log.error("Error enviando webhook para alerta sku={}: {}", alerta.getSku(), e.getMessage());
            return false;
        }
    }

    private String buildWebhookText(StockAlert alerta) {
        String emoji = switch (alerta.getTipo()) {
            case SIN_STOCK           -> "🔴";
            case AGOTAMIENTO_CERCANO -> "🟠";
            case STOCK_BAJO          -> "🟡";
            case ANOMALIA_VENTAS     -> "⚠️";
            case REPOSICION_SUGERIDA -> "📦";
        };
        return String.format("%s *%s* — %s | Stock: %d unidades | %s",
                emoji, alerta.getNombre(), alerta.getTipo().name().replace("_", " "),
                alerta.getStockActual(), alerta.getMensaje());
    }
}
