package com.tuempresa.stocksync.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MLWebhookPayload {

    private String resource;
    private String topic;
    @JsonProperty("user_id")
    private Long userId;
    @JsonProperty("application_id")
    private Long applicationId;
    private Long sent;
    private List<String> attempts;

    // Extrae el ID del recurso desde la URL del resource
    // ej: /orders/123456789 → "123456789"
    public String extractResourceId() {
        if (resource == null) return null;
        String[] parts = resource.split("/");
        return parts[parts.length - 1];
    }
}
