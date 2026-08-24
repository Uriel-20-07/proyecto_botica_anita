package com.example.demo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración central de Spring Security para la aplicación.
 * 
 * Define las reglas de autorización para cada endpoint de la API REST y
 * registra el filtro JWT personalizado en la cadena de seguridad.
 * 
 * Estrategia de autenticación: STATELESS (sin sesión en servidor), usando
 * tokens JWT enviados en el encabezado "Authorization: Bearer <token>".
 */
@Configuration
public class SecurityConfig {

    /** Filtro que extrae y valida el token JWT de cada petición HTTP. */
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Define la cadena de filtros de seguridad (SecurityFilterChain).
     *
     * Configuraciones aplicadas:
     * - CSRF deshabilitado: no es necesario para APIs REST con JWT.
     * - CORS habilitado con la configuración definida en {@link WebConfig}.
     * - Política de sesión STATELESS: no se crea ni usa HttpSession.
     * - Reglas de autorización por endpoint:
     * * /api/auth/** → público (registro, login, recuperación de contraseña).
     * * /api/admin/auth/** → público (login de administradores).
     * * /api/admin/** → requiere autenticación (JWT válido).
     * * /api/carrito/** → requiere autenticación (JWT válido).
     * * Cualquier otra ruta → pública.
     * - El filtro JWT se ejecuta ANTES del filtro de usuario/contraseña estándar.
     *
     * @param http objeto configurador de seguridad HTTP.
     * @return la cadena de filtros construida.
     * @throws Exception si falla alguna configuración.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Deshabilita la protección CSRF (no necesaria en APIs REST sin sesión)
                .csrf(AbstractHttpConfigurer::disable)
                // Activa CORS usando la configuración global (WebConfig)
                .cors(Customizer.withDefaults())
                // No se almacena estado de sesión en el servidor
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Define qué endpoints requieren autenticación
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // Rutas públicas de usuario
                        .requestMatchers("/api/admin/auth/**").permitAll() // Login de administrador (público)
                        .requestMatchers("/api/admin/**").authenticated() // Panel admin (requiere JWT)
                        .requestMatchers("/api/carrito/**").authenticated() // Protegemos el carrito
                        .requestMatchers("/api/recetas/**").authenticated() // Protegemos recetas médicas
                        .requestMatchers("/api/pedidos/**").authenticated() // Protegemos el historial de pedidos
                        .anyRequest().permitAll() // Resto de rutas: públicas
                )
                // Agrega el filtro JWT antes del filtro estándar de usuario/contraseña
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
} 