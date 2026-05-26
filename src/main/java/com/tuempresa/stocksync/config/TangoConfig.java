package com.tuempresa.stocksync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tango")
public class TangoConfig {
    private String baseUrl;
    private String apiKey;
    private String empresa;
}
