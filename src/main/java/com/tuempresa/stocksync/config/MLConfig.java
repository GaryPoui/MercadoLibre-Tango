package com.tuempresa.stocksync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mercadolibre")
public class MLConfig {
    private String clientId;
    private String clientSecret;
    private String webhookSecret;
    private String baseUrl;
    private String sellerId;
}
