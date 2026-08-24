package com.example.demo.controllers;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.models.User;
import com.example.demo.services.AuthService;

/**
 * Controlador REST para la autenticación de usuarios clientes.
 * 
 * Expone los endpoints bajo la ruta base: /api/auth
 * Todos estos endpoints son PÚBLICOS (no requieren JWT), ya que son
 * los puntos de entrada al sistema para usuarios no autenticados.
 * 
 * Endpoints disponibles:
 * - POST /api/auth/registro              → Registro de nuevo usuario.
 * - POST /api/auth/login                 → Login y obtención de token JWT.
 * - POST /api/auth/solicitar-recuperacion → Solicitar recuperación por email.
 * - GET  /api/auth/validar-token/{token} → Validar token de recuperación.
 * - POST /api/auth/cambiar-contrasena    → Cambiar contraseña con token.
 * - POST /api/auth/cambiar-contrasena-autenticado → Cambiar contraseña (usuario logueado).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Servicio de autenticación con la lógica de negocio. */
    @Autowired
    private AuthService authService;

    /**
     * Registra un nuevo usuario en el sistema.
     * 
     * Valida que email, password y nombre estén presentes antes de delegar
     * al servicio. En caso de éxito, retorna HTTP 201 (Created) con los
     * datos básicos del usuario (sin contraseña).
     * 
     * Body esperado (JSON):
     * {
     *   "email": "usuario@email.com",
     *   "password": "min6chars",
     *   "nombre": "Juan",
     *   "apellido": "Perez",
     *   "telefono": "987654321"
     * }
     *
     * @param request mapa con los datos del nuevo usuario.
     * @return 201 con datos del usuario, o 400 con mensaje de error.
     */
    @PostMapping("/registro")
    public ResponseEntity<?> registro(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String password = request.get("password");
            String nombre = request.get("nombre");
            String apellido = request.get("apellido");
            String telefono = request.get("telefono");

            // Validaciones básicas de campos obligatorios
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El email es requerido"));
            }
            if (password == null || password.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La contraseña es requerida"));
            }
            if (nombre == null || nombre.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El nombre es requerido"));
            }

            User usuario = authService.registrar(email, password, nombre, apellido, telefono);

            // Respuesta de éxito: no incluir la contraseña
            Map<String, Object> response = new HashMap<>();
            response.put("mensaje", "Usuario registrado exitosamente");
            response.put("usuario", Map.of(
                    "id", usuario.getId(),
                    "email", usuario.getEmail(),
                    "nombre", usuario.getNombre(),
                    "apellido", usuario.getApellido()
            ));

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Autentica un usuario y retorna un token JWT.
     * 
     * Body esperado (JSON):
     * { "email": "usuario@email.com", "password": "contraseña" }
     * 
     * Respuesta exitosa:
     * { "token": "eyJ...", "usuario": { "id": 1, "email": "...", ... } }
     *
     * @param request mapa con email y password del usuario.
     * @return 200 con token y datos del usuario, o 401 con mensaje de error.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String password = request.get("password");

            if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email y contraseña son requeridos"));
            }

            String token = authService.login(email, password);
            User usuario = authService.obtenerUsuarioPorEmail(email);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("usuario", Map.of(
                    "id", usuario.getId(),
                    "email", usuario.getEmail(),
                    "nombre", usuario.getNombre(),
                    "apellido", usuario.getApellido()
            ));

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Inicia el flujo de recuperación de contraseña enviando un email al usuario.
     * 
     * Por seguridad, siempre responde con 200 OK aunque el email no exista,
     * para no revelar si un email está registrado en el sistema.
     * 
     * Body esperado: { "email": "usuario@email.com" }
     *
     * @param request mapa con el email del usuario.
     * @return 200 con mensaje genérico, o 500 si hay un error del servidor.
     */
    @PostMapping("/solicitar-recuperacion")
    public ResponseEntity<?> solicitarRecuperacion(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");

            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El email es requerido"));
            }

            authService.solicitarRecuperacionContraseña(email);

            // Mensaje genérico para no revelar si el email existe
            return ResponseEntity.ok(Map.of("mensaje", 
                    "Si el email existe en nuestro sistema, recibirás un enlace para recuperar tu contraseña"));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al procesar la solicitud"));
        }
    }

    /**
     * Valida si un token de recuperación de contraseña es válido y no ha expirado.
     * El frontend llama a este endpoint al cargar la página de reset para verificar
     * que el enlace del email sigue siendo válido.
     *
     * @param token token UUID de recuperación (en la URL).
     * @return 200 con { "valido": true } si es válido, o 400 si expiró/no existe.
     */
    @GetMapping("/validar-token/{token}")
    public ResponseEntity<?> validarToken(@PathVariable String token) {
        try {
            boolean valido = authService.validarTokenRecuperacion(token);

            if (valido) {
                return ResponseEntity.ok(Map.of("valido", true));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Token inválido o expirado"));
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Error al validar el token"));
        }
    }

    /**
     * Cambia la contraseña del usuario usando un token de recuperación.
     * 
     * Body esperado:
     * { "token": "uuid-token", "nuevaContraseña": "nuevaPass123" }
     *
     * @param request mapa con el token y la nueva contraseña.
     * @return 200 con mensaje de éxito, o 400 con mensaje de error.
     */
    @PostMapping("/cambiar-contrasena")
    public ResponseEntity<?> cambiarContraseña(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            String nuevaContraseña = request.get("nuevaContraseña");

            if (token == null || token.isEmpty() || nuevaContraseña == null || nuevaContraseña.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Token y nueva contraseña son requeridos"));
            }

            authService.cambiarContraseña(token, nuevaContraseña);

            return ResponseEntity.ok(Map.of("mensaje", "Contraseña cambiada exitosamente"));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Endpoint para cambiar contraseña cuando el usuario YA está autenticado.
     * 
     * Extrae el email del token JWT a través del objeto {@link Principal} inyectado
     * por Spring Security, luego delega al servicio para validar la contraseña
     * actual y establecer la nueva.
     * 
     * Body esperado:
     * { "contraseñaActual": "actual", "nuevaContraseña": "nueva" }
     *
     * @param request   mapa con contraseña actual y nueva.
     * @param principal usuario autenticado (inyectado por Spring Security con el email del JWT).
     * @return 200 con mensaje de éxito, 401 si no autenticado, o 400 con mensaje de error.
     */
    @PostMapping("/cambiar-contrasena-autenticado")
    public ResponseEntity<?> cambiarContraseñaAutenticado(@RequestBody Map<String, String> request,
                                                          Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Usuario no autenticado"));
        }
        try {
            String contraseñaActual = request.get("contraseñaActual");
            String nuevaContraseña = request.get("nuevaContraseña");

            if (contraseñaActual == null || contraseñaActual.isEmpty()
                    || nuevaContraseña == null || nuevaContraseña.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Ambas contraseñas son requeridas"));
            }

            // El email del usuario autenticado se extrae del JWT a través del Principal
            String email = principal.getName();
            authService.cambiarContraseñaAutenticado(email, contraseñaActual, nuevaContraseña);

            return ResponseEntity.ok(Map.of("mensaje", "Contraseña cambiada exitosamente"));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno al cambiar la contraseña"));
        }
    }
}
 