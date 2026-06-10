package com.tuempresa.stocksync.config;

import com.tuempresa.stocksync.model.Rol;
import com.tuempresa.stocksync.model.Usuario;
import com.tuempresa.stocksync.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Crea un usuario admin por defecto si no existe ninguno.
 * Las credenciales se toman de variables de entorno.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${security.admin.username:admin}")
    private String adminUsername;

    @Value("${security.admin.password:${ADMIN_PASSWORD}}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (!usuarioRepository.existsByUsername(adminUsername)) {
            Usuario admin = Usuario.builder()
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .email("admin@stocksync.local")
                    .roles(Set.of(Rol.ADMIN))
                    .enabled(true)
                    .build();
            usuarioRepository.save(admin);
            log.info("Usuario admin creado exitosamente");
        }
    }
}
