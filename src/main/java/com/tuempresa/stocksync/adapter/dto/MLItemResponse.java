package com.tuempresa.stocksync.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MLItemResponse {

    private String id;
    private String title;
    @JsonProperty("seller_sku")
    private String sellerSku;
    @JsonProperty("available_quantity")
    private int availableQuantity;
    private String status;
    private String condition;
}
