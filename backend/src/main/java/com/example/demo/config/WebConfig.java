package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración global de CORS (Cross-Origin Resource Sharing) para la API REST.
 * 
 * CORS es una política de seguridad del navegador que impide que un script de un
 * dominio realice peticiones a otro dominio diferente. Esta clase define qué orígenes
 * tienen permitido consumir la API del backend.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Registra las reglas de CORS para todos los endpoints de la API.
     *
     * - Permite peticiones desde el cliente Angular en puerto 4200 y 62349 (dev),
     *   así como cualquier puerto de localhost durante el desarrollo.
     * - Permite los métodos HTTP: GET, POST, PUT, DELETE y OPTIONS (preflight).
     * - Permite cualquier encabezado HTTP en la petición.
     * - Permite el envío de credenciales (cookies, tokens de autorización).
     *
     * @param registry registro de mapeos CORS de Spring MVC.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:4200", "http://localhost:62349", "http://localhost:*", "https://witty-bay-08b8c990f.7.azurestaticapps.net", "https://purple-field-0b677da0f.7.azurestaticapps.net")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        java.nio.file.Path path = java.nio.file.Paths.get("uploads");
        String absolutePath = path.toAbsolutePath().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
 