package com.example.demo.controllers;

import com.example.demo.models.Calificacion;
import com.example.demo.models.Pedido;
import com.example.demo.models.User;
import com.example.demo.repositories.AdministradorRepository;
import com.example.demo.services.AuthService;
import com.example.demo.services.CalificacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Satisfacción del cliente.
 *
 * Cliente (requiere sesión):
 *   GET  /api/calificaciones/pendiente  → ¿hay un pedido listo para calificar?
 *   POST /api/calificaciones            → registra la calificación de un pedido propio
 *
 * Personal de la botica (requiere sesión de administrador):
 *   GET  /api/admin/satisfaccion        → resumen para el dashboard
 */
@RestController
public class CalificacionController {

    @Autowired
    private CalificacionService calificacionService;

    @Autowired
    private AuthService authService;

    @Autowired
    private AdministradorRepository administradorRepository;

    /** Indica si el cliente tiene un pedido pendiente de calificar. */
    @GetMapping("/api/calificaciones/pendiente")
    public ResponseEntity<?> pendiente(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuario no autenticado"));
        }
        User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
        Map<String, Object> respuesta = new LinkedHashMap<>();
        Pedido pedido = calificacionService.buscarPedidoPendiente(usuario).orElse(null);
        if (pedido == null) {
            respuesta.put("pendiente", false);
        } else {
            respuesta.put("pendiente", true);
            respuesta.put("idPedido", pedido.getIdPedido());
            respuesta.put("estado", pedido.getEstado());
        }
        return ResponseEntity.ok(respuesta);
    }

    /** Registra la calificación (1 a 5 estrellas y comentario opcional) de un pedido del cliente. */
    @PostMapping("/api/calificaciones")
    public ResponseEntity<?> calificar(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuario no autenticado"));
        }
        try {
            User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
            Integer idPedido = aEntero(body.get("idPedido"));
            Integer estrellas = aEntero(body.get("estrellas"));
            Object comentario = body.get("comentario");
            Calificacion guardada = calificacionService.registrar(
                    usuario, idPedido, estrellas, comentario == null ? null : comentario.toString());
            return ResponseEntity.ok(Map.of(
                    "mensaje", "¡Gracias por tu opinión!",
                    "idCalificacion", guardada.getIdCalificacion()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", e.getReason() == null ? "Solicitud inválida" : e.getReason()));
        } catch (DataIntegrityViolationException e) {
            // Dos envíos simultáneos para el mismo pedido
            return ResponseEntity.status(409).body(Map.of("error", "Este pedido ya fue calificado"));
        }
    }

    /** Resumen de satisfacción para el dashboard (solo personal de la botica). */
    @GetMapping("/api/admin/satisfaccion")
    public ResponseEntity<?> resumen() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null
                || administradorRepository.findByCorreoCorp(auth.getName()).isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }
        return ResponseEntity.ok(calificacionService.resumen());
    }

    private Integer aEntero(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            return Integer.valueOf(valor.toString().trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dato numérico inválido");
        }
    }
}
