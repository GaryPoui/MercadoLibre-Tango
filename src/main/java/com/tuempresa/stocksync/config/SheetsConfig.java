package com.tuempresa.stocksync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "google.sheets")
public class SheetsConfig {
    private String spreadsheetId;
    private String credentialsPath;
    private String stockSheetName;
    private String skuColumn;
    private String stockColumn;
    private String nameColumn;
    private String priceColumn;
}
