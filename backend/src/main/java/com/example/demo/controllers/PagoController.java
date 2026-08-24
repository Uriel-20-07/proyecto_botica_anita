package com.example.demo.controllers;

import com.example.demo.dto.PagoRequest;
import com.example.demo.models.User;
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
