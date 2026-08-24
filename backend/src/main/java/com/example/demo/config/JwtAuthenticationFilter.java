package com.example.demo.config;

import com.example.demo.services.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Filtro HTTP que intercepta cada petición entrante para validar el token JWT.
 * 
 * Extiende {@link OncePerRequestFilter} para garantizar que la lógica de
 * autenticación se ejecute una sola vez por solicitud HTTP, incluso en
 * cadenas de filtros complejas.
 * 
 * Flujo de ejecución:
 * 1. Extrae el token JWT del encabezado "Authorization: Bearer <token>".
 * 2. Valida el token con {@link JwtService}.
 * 3. Si es válido, extrae el email del payload y crea un objeto de autenticación.
 * 4. Registra la autenticación en el {@link SecurityContextHolder} para que
 *    los controladores puedan acceder al usuario autenticado.
 * 5. Siempre cede el control al siguiente filtro de la cadena.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Servicio que genera, valida y parsea tokens JWT. */
    @Autowired
    private JwtService jwtService;

    /**
     * Lógica principal del filtro: procesa el JWT si existe y es válido.
     *
     * @param request     petición HTTP entrante.
     * @param response    respuesta HTTP que se enviará al cliente.
     * @param filterChain cadena de filtros; se llama a {@code doFilter} para continuar.
     * @throws ServletException si ocurre un error de servlet.
     * @throws IOException      si ocurre un error de E/S.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Extrae el token del encabezado Authorization
            String jwt = parseJwt(request);

            if (jwt != null && jwtService.validateToken(jwt)) {
                // Obtiene el email (subject) del token
                String email = jwtService.getEmailFromToken(jwt);

                // Crea el token de autenticación de Spring Security.
                // Lista de roles vacía: los roles se podrían agregar en el futuro.
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        email, null, new ArrayList<>());

                // Agrega detalles de la petición (IP, session ID) al objeto de autenticación
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Registra el usuario autenticado en el contexto de seguridad de Spring
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // Si el token es inválido o ha expirado, simplemente no se establece autenticación
            logger.error("No se pudo establecer la autenticación del usuario: {}", e);
        }

        // Continúa con el siguiente filtro en la cadena (siempre se ejecuta)
        filterChain.doFilter(request, response);
    }

    /**
     * Extrae el token JWT del encabezado "Authorization" de la petición.
     * El formato esperado es: "Bearer <token>".
     *
     * @param request petición HTTP entrante.
     * @return el token JWT como String, o {@code null} si no está presente o mal formado.
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        // Verifica que el encabezado exista y comience con "Bearer "
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7); // Elimina el prefijo "Bearer " (7 caracteres)
        }
        return null;
    }
}