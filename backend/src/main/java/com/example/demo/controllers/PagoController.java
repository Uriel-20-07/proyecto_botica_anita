package com.example.demo.controllers;

import com.example.demo.dto.PagoRequest;
import com.example.demo.models.User;
import com.example.demo.repositories.AdministradorRepository;
import com.example.demo.services.AuthService;
import com.example.demo.services.PagoService;
import com.stripe.model.PaymentIntent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

/**
 * Controlador REST para el procesamiento de pagos.
 * * Ruta base: /api/pago
 * CORS: permite peticiones desde Angular (localhost:4200).
 */
@RestController
@RequestMapping("/api/pago")
public class PagoController {

    @Autowired private PagoService pagoService;
    @Autowired private AuthService authService;
    @Autowired private AdministradorRepository administradorRepository;

    /**
     * NUEVO ENDPOINT PARA STRIPE:
     * Crea una intención de pago (PaymentIntent) devolviendo el client_secret a Angular.
     */
    @PostMapping("/create-payment-intent")
    public ResponseEntity<?> createPaymentIntent(@RequestBody PagoRequest request, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Usuario no autenticado"));
        try {
            User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
            PaymentIntent intent = pagoService.crearPaymentIntent(usuario.getId(), request);
            
            // Retornamos el secreto que Angular necesita para abrir el formulario de tarjeta
            return ResponseEntity.ok(Map.of("clientSecret", intent.getClientSecret()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Endpoint original: Procesa el pago final (Yape o confirmación post-Stripe).
     */
    @PostMapping("/procesar")
    public ResponseEntity<?> procesarPago(@RequestBody PagoRequest request, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Usuario no autenticado"));
        try {
            User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
            pagoService.procesarTransaccion(usuario.getId(), request);
            return ResponseEntity.ok(Map.of("message", "Pago procesado con éxito"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Datos de la cuenta Plin de la botica (número y titular) para mostrarlos en el checkout.
     */
    @GetMapping("/plin-info")
    public ResponseEntity<?> plinInfo() {
        return ResponseEntity.ok(pagoService.obtenerInfoPlin());
    }

    /**
     * Personal de la botica: confirma que el pago por Plin llegó a la cuenta.
     */
    @PatchMapping("/plin/{idPedido}/confirmar")
    public ResponseEntity<?> confirmarPlin(@PathVariable Integer idPedido, Principal principal) {
        ResponseEntity<?> denegado = validarPersonal(principal);
        if (denegado != null) return denegado;
        try {
            pagoService.confirmarPagoPlin(idPedido);
            return ResponseEntity.ok(Map.of("message", "Pago Plin confirmado"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Personal de la botica: rechaza el pago por Plin (no se encontró la operación).
     */
    @PatchMapping("/plin/{idPedido}/rechazar")
    public ResponseEntity<?> rechazarPlin(@PathVariable Integer idPedido, Principal principal) {
        ResponseEntity<?> denegado = validarPersonal(principal);
        if (denegado != null) return denegado;
        try {
            pagoService.rechazarPagoPlin(idPedido);
            return ResponseEntity.ok(Map.of("message", "Pago Plin rechazado"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Devuelve una respuesta 401/403 si quien llama no es personal (admin/vendedor); null si es válido. */
    private ResponseEntity<?> validarPersonal(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Usuario no autenticado"));
        if (administradorRepository.findByCorreoCorp(principal.getName()).isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("error", "Acceso restringido al personal de la botica"));
        }
        return null;
    }

    /**
     * Endpoint para validar un cupón de descuento.
     */
    @GetMapping("/validar-cupon/{codigo}")
    public ResponseEntity<?> validarCupon(@PathVariable String codigo, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Usuario no autenticado"));
        try {
            User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
            Map<String, Object> resultado = pagoService.validarCupon(usuario.getId(), codigo);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
