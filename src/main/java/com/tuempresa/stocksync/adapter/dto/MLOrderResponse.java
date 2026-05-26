package com.tuempresa.stocksync.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MLOrderResponse {

    private Long id;
    private String status;

    @JsonProperty("order_items")
    private List<MLOrderItem> orderItems;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MLOrderItem {
        private MLItem item;
        @JsonProperty("quantity")
        private int quantity;
        @JsonProperty("unit_price")
        private double unitPrice;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MLItem {
        private String id;
        @JsonProperty("seller_sku")
        private String sellerSku;
        private String title;
    }
}
