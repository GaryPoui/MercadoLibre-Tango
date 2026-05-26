package com.tuempresa.stocksync.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class GoogleSheetsConfig {

    private final SheetsConfig sheetsConfig;

    @Bean
    public Sheets sheetsService() throws GeneralSecurityException, IOException {
        GoogleCredentials credentials;

        try (InputStream credentialsStream = new ClassPathResource(
                sheetsConfig.getCredentialsPath()).getInputStream()) {
            credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(List.of(SheetsScopes.SPREADSHEETS));
        }

        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("StockSync")
                .build();
    }
}
