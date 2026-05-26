package com.tuempresa.stocksync.adapter;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.tuempresa.stocksync.config.SheetsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class SheetsClient {

    private final Sheets sheetsService;
    private final SheetsConfig sheetsConfig;

    /**
     * Lee todos los productos del spreadsheet.
     * Columnas esperadas: A=SKU, B=Stock, C=Nombre, D=Precio
     */
    @Retryable(retryFor = IOException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public Map<String, SheetRow> readAllStock() throws IOException {
        String range = sheetsConfig.getStockSheetName() + "!A2:E";
        ValueRange response = sheetsService.spreadsheets().values()
                .get(sheetsConfig.getSpreadsheetId(), range)
                .execute();

        Map<String, SheetRow> result = new HashMap<>();
        List<List<Object>> values = response.getValues();
        if (values == null) return result;

        for (int i = 0; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row.isEmpty()) continue;

            String sku = row.get(0).toString().trim();
            int stock = row.size() > 1 ? parseIntSafe(row.get(1).toString()) : 0;
            String nombre = row.size() > 2 ? row.get(2).toString() : "";
            BigDecimal precio = row.size() > 3 ? parseBigDecimalSafe(row.get(3).toString()) : BigDecimal.ZERO;

            result.put(sku, new SheetRow(sku, stock, nombre, precio, i + 2)); // +2 por header + base 1
        }
        return result;
    }

    /**
     * Actualiza el stock de un SKU en la hoja.
     */
    @Retryable(retryFor = IOException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void updateStock(String sku, int nuevoStock, int sheetsRow) throws IOException {
        log.info("Actualizando stock Sheets: sku={} nuevoStock={} fila={}", sku, nuevoStock, sheetsRow);
        String range = sheetsConfig.getStockSheetName() + "!B" + sheetsRow;
        ValueRange body = new ValueRange()
                .setValues(List.of(List.of(nuevoStock)));

        sheetsService.spreadsheets().values()
                .update(sheetsConfig.getSpreadsheetId(), range, body)
                .setValueInputOption("RAW")
                .execute();
    }

    /**
     * Busca la fila de un SKU específico.
     */
    public int findRowBySku(String sku) throws IOException {
        Map<String, SheetRow> all = readAllStock();
        SheetRow row = all.get(sku);
        return row != null ? row.rowNumber() : -1;
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal parseBigDecimalSafe(String value) {
        try {
            return new BigDecimal(value.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public record SheetRow(String sku, int stock, String nombre, BigDecimal precio, int rowNumber) {}
}
