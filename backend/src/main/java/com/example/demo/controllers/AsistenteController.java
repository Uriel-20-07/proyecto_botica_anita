package com.example.demo.controllers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.PedidoEmailDTO;
import com.example.demo.models.Administrador;
import com.example.demo.models.Pedido;
import com.example.demo.models.Producto;
import com.example.demo.models.User;
import com.example.demo.repositories.AdministradorRepository;
import com.example.demo.repositories.PedidoRepository;
import com.example.demo.repositories.ProductoRepository;
import com.example.demo.services.EmailService;
import com.example.demo.services.JwtService;
import com.example.demo.services.PagoService;
import com.example.demo.services.VentaPresencialService;

/**
 * Controlador para el rol "asistente" (personal de tienda).
 * Ruta base: /api/asistente
 *
 * Funcionalidades:
 *  - Venta presencial (POS): reduce stock y genera boleta.
 *  - Confirmación de entrega de pedidos (delivery y recojo).
 *  - Historial de ventas del día.
 */
@RestController
@RequestMapping("/api/asistente")
public class AsistenteController {

    @Autowired
    private AdministradorRepository administradorRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private VentaPresencialService ventaPresencialService;

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    // Constraint fix moved to ConstraintFixConfig (CommandLineRunner with transaction).

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El correo y contrase\u00f1a son requeridos"));
        }

        Optional<Administrador> adminOpt = administradorRepository.findByCorreoCorp(email);
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales corporativas inv\u00e1lidas"));
        }

        Administrador admin = adminOpt.get();
        if (!"asistente".equalsIgnoreCase(admin.getRol())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No tienes permisos de asistente"));
        }
        if (!passwordEncoder.matches(password, admin.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales corporativas inv\u00e1lidas"));
        }

        String token = jwtService.generateToken(admin.getCorreoCorp());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("usuario", Map.of(
                "idAdmin", admin.getIdAdmin(),
                "nombre", admin.getNombre(),
                "correoCorp", admin.getCorreoCorp(),
                "rol", admin.getRol()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Lista de productos disponibles para el POS.
     * Solo incluye lo que se puede vender: stock físico menos reservas de carritos.
     */
    @GetMapping("/productos")
    public ResponseEntity<?> listarProductos() {
        List<Producto> productos = productoRepository.findAll().stream()
                .filter(p -> p.getStockDisponible() > 0 && !p.isAgotado())
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(productos);
    }

    /**
     * Crea una venta presencial (POS).
     * Body: { "items": [{"idProducto": 1, "cantidad": 2}, ...], "metodoPago": "EFECTIVO", "emailCliente": "..." (opcional) }
     */
    @PostMapping("/venta")
    public ResponseEntity<?> crearVentaPresencial(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");
            String metodoPago = request.get("metodoPago") != null ? String.valueOf(request.get("metodoPago")) : "EFECTIVO";
            String emailCliente = request.get("emailCliente") != null ? String.valueOf(request.get("emailCliente")) : null;
            String nombreCliente = request.get("nombreCliente") != null ? String.valueOf(request.get("nombreCliente")) : "Cliente en tienda";

            // La venta completa (pedido + detalles + stock) se ejecuta en una sola
            // transacción dentro del servicio: si algo falla, no queda nada a medias.
            Map<String, Object> response = ventaPresencialService.registrarVenta(
                    items, metodoPago, emailCliente, nombreCliente);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lista de pedidos pendientes de confirmación (delivery y recojo).
     */
    @GetMapping("/pedidos")
    public ResponseEntity<?> listarPedidosPendientes() {
        List<Pedido> pedidos = pedidoRepository.findAll().stream()
                .filter(p -> {
                    String est = p.getEstado() != null ? p.getEstado().toUpperCase() : "";
                    if (est.contains("COMPLETAD") || est.contains("ENTREGAD")
                            || est.contains("CANCELAD") || est.contains("RECHAZAD")
                            || est.contains("SUSPENDID")) return false;
                    // Excluir ventas presenciales (solo pedidos de clientes: delivery y recojo)
                    if ("Venta en tienda".equalsIgnoreCase(p.getDireccionEnvio())) return false;
                    return true;
                })
                .sorted((a, b) -> b.getIdPedido().compareTo(a.getIdPedido()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(pedidos);
    }

    /**
     * Marca un pedido como entregado (el cliente recibió el pedido).
     */
    @PutMapping("/pedidos/{id}/entregar")
    public ResponseEntity<?> marcarEntregado(@PathVariable Integer id) {
        Optional<Pedido> pedidoOpt = pedidoRepository.findById(id);
        if (pedidoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pedido no encontrado"));
        }
        Pedido pedido = pedidoOpt.get();
        String est = estadoActual(pedido);
        if (esEstadoFinal(est)) {
            return ResponseEntity.ok(Map.of("ok", true, "estado", est,
                    "mensaje", "El pedido ya tiene un estado final"));
        }
        pedido.setEstado("COMPLETADO");
        pedidoRepository.save(pedido);
        notificarCliente(pedido, emailService::sendEntregadoEmail);
        return ResponseEntity.ok(Map.of("ok", true, "estado", "COMPLETADO"));
    }

    /**
     * Marca un pedido como suspendido / no entregado (no había nadie en la
     * dirección al momento de la entrega).
     */
    @PutMapping("/pedidos/{id}/suspender")
    public ResponseEntity<?> marcarSuspendido(@PathVariable Integer id) {
        Optional<Pedido> pedidoOpt = pedidoRepository.findById(id);
        if (pedidoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pedido no encontrado"));
        }
        Pedido pedido = pedidoOpt.get();
        String est = estadoActual(pedido);
        if (esEstadoFinal(est)) {
            return ResponseEntity.ok(Map.of("ok", true, "estado", est,
                    "mensaje", "El pedido ya tiene un estado final"));
        }
        pedido.setEstado("SUSPENDIDO");
        pedidoRepository.save(pedido);
        notificarCliente(pedido, emailService::sendPedidoSuspendidoEmail);
        return ResponseEntity.ok(Map.of("ok", true, "estado", "SUSPENDIDO"));
    }

    /**
     * Marca un pedido como cancelado (la receta médica entregada al motorizado
     * no era la correcta).
     */
    @PutMapping("/pedidos/{id}/cancelar")
    public ResponseEntity<?> marcarCancelado(@PathVariable Integer id) {
        Optional<Pedido> pedidoOpt = pedidoRepository.findById(id);
        if (pedidoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pedido no encontrado"));
        }
        Pedido pedido = pedidoOpt.get();
        String est = estadoActual(pedido);
        if (esEstadoFinal(est)) {
            return ResponseEntity.ok(Map.of("ok", true, "estado", est,
                    "mensaje", "El pedido ya tiene un estado final"));
        }
        pedido.setEstado("CANCELADO");
        pedidoRepository.save(pedido);
        notificarCliente(pedido, emailService::sendPedidoCanceladoEmail);
        return ResponseEntity.ok(Map.of("ok", true, "estado", "CANCELADO"));
    }

    /**
     * Lista de pedidos finalizados: entregados, suspendidos y cancelados.
     * Conserva el nombre "entregados" como alias por compatibilidad.
     */
    @GetMapping({"/pedidos/finalizados", "/pedidos/entregados"})
    public ResponseEntity<?> listarPedidosFinalizados() {
        List<Pedido> pedidos = pedidoRepository.findAll().stream()
                .filter(p -> {
                    String est = p.getEstado() != null ? p.getEstado().toUpperCase() : "";
                    return est.contains("COMPLETAD") || est.contains("ENTREGAD")
                            || est.contains("SUSPENDID") || est.contains("CANCELAD");
                })
                .sorted((a, b) -> b.getIdPedido().compareTo(a.getIdPedido()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(pedidos);
    }

    private String estadoActual(Pedido pedido) {
        return pedido.getEstado() != null ? pedido.getEstado().toUpperCase() : "";
    }

    private boolean esEstadoFinal(String estado) {
        return estado.contains("COMPLETAD") || estado.contains("ENTREGAD")
                || estado.contains("CANCELAD") || estado.contains("SUSPENDID")
                || estado.contains("RECHAZAD");
    }

    /**
     * Extrae los datos planos del pedido dentro de la transacción y delega el
     * envío asíncrono del correo (sin tocar entidades JPA en el otro hilo).
     */
    private void notificarCliente(Pedido pedido, java.util.function.Consumer<PedidoEmailDTO> envio) {
        User u = pedido.getUsuario();
        if (u != null && u.getEmail() != null && !u.getEmail().isBlank()) {
            String nro = pedido.getIdPedido() != null ? String.format("%06d", pedido.getIdPedido()) : "";
            PedidoEmailDTO emailDto = PedidoEmailDTO.from(pedido, u, null, null, nro);
            envio.accept(emailDto);
        }
    }

    /**
     * Historial de ventas presenciales del día.
     */
    @GetMapping("/ventas/historial")
    public ResponseEntity<?> historialVentasHoy() {
        java.time.LocalDate hoy = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        List<Pedido> ventas = pedidoRepository.findAll().stream()
                .filter(p -> "Venta en tienda".equals(p.getDireccionEnvio()))
                .filter(p -> p.getFecha() != null && p.getFecha().toLocalDate().equals(hoy))
                .sorted((a, b) -> b.getIdPedido().compareTo(a.getIdPedido()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ventas);
    }
}
