package com.example.demo.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults()) // 🟢 Activa la configuración del Bean corsConfigurationSource
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );

        return http.build();
    }

    // Orígenes adicionales permitidos en producción (dominio de Azure Static Web Apps,
    // y un dominio propio si lo configuras más adelante). Se puede sobreescribir con la
    // variable de entorno CORS_ALLOWED_ORIGINS (orígenes separados por coma), sin tocar código.
    @Value("${CORS_ALLOWED_ORIGINS:https://purple-field-0b677da0f.azurestaticapps.net}")
    private String corsAllowedOriginsProd;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origenesPermitidos = new java.util.ArrayList<>(List.of(
                "http://localhost:4200",
                "http://localhost:*"
        ));
        for (String origen : corsAllowedOriginsProd.split(",")) {
            String limpio = origen.trim();
            if (!limpio.isEmpty()) {
                origenesPermitidos.add(limpio);
            }
        }

        // 🟢 USA setAllowedOriginPatterns en lugar de setAllowedOrigins con "*"
        configuration.setAllowedOriginPatterns(origenesPermitidos);
        
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true); // Ya no lanzará excepción

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}