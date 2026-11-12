package com.example.demo.controllers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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

import com.example.demo.models.Administrador;
import com.example.demo.models.DetallePedido;
import com.example.demo.models.Pedido;
import com.example.demo.models.Producto;
import com.example.demo.models.User;
import com.example.demo.repositories.AdministradorRepository;
import com.example.demo.repositories.DetallePedidoRepository;
import com.example.demo.repositories.PedidoRepository;
import com.example.demo.repositories.ProductoRepository;
import com.example.demo.services.EmailService;
import com.example.demo.services.JwtService;
import com.example.demo.services.PagoService;

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
    private DetallePedidoRepository detallePedidoRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PagoService pagoService;

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
     * Lista de productos disponibles (stock > 0) para el POS.
     */
    @GetMapping("/productos")
    public ResponseEntity<?> listarProductos() {
        List<Producto> productos = productoRepository.findAll().stream()
                .filter(p -> p.getStock() != null && p.getStock() > 0 && !p.isAgotado())
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

            if (items == null || items.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El carrito est\u00e1 vac\u00edo"));
            }

            // Validar stock
            for (Map<String, Object> item : items) {
                Integer idProd = Integer.valueOf(String.valueOf(item.get("idProducto")));
                int cant = Integer.parseInt(String.valueOf(item.get("cantidad")));
                Producto prod = productoRepository.findById(idProd).orElse(null);
                if (prod == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado: " + idProd));
                }
                if (prod.getStock() == null || prod.getStock() < cant) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Stock insuficiente para " + prod.getNombre()));
                }
            }

            // Crear pedido
            Pedido pedido = new Pedido();
            pedido.setFecha(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            pedido.setEstado("PAGADO");
            pedido.setMetodoPago(metodoPago);
            pedido.setEsUrgente(false);
            pedido.setDireccionEnvio("Venta en tienda");
            pedido.setDistrito("Independencia");

            // Calcular total
            double total = 0.0;
            List<com.example.demo.models.DetallePedido> detalles = new ArrayList<>();
            for (Map<String, Object> item : items) {
                Integer idProd = Integer.valueOf(String.valueOf(item.get("idProducto")));
                int cant = Integer.parseInt(String.valueOf(item.get("cantidad")));
                Producto prod = productoRepository.findById(idProd).orElseThrow();
                double precio = prod.getPrecioVenta() != null ? prod.getPrecioVenta().doubleValue() : 0.0;
                total += precio * cant;

                com.example.demo.models.DetallePedido det = new com.example.demo.models.DetallePedido();
                det.setPedido(pedido);
                det.setProducto(prod);
                det.setCantidad(cant);
                det.setPrecioHistorico(prod.getPrecioVenta());
                detalles.add(det);
            }
            pedido.setTotal(java.math.BigDecimal.valueOf(total));
            pedidoRepository.save(pedido);

            // Guardar detalles y descontar stock
            for (com.example.demo.models.DetallePedido det : detalles) {
                detallePedidoRepository.save(det);
                // Descontar stock
                Producto prod = det.getProducto();
                int nuevoStock = (prod.getStock() != null ? prod.getStock() : 0) - det.getCantidad();
                prod.setStock(Math.max(0, nuevoStock));
                productoRepository.save(prod);
            }

            // Enviar boleta por email si hay email del cliente
            if (emailCliente != null && !emailCliente.isBlank()) {
                try {
                    User u = new User();
                    u.setNombre(nombreCliente);
                    u.setEmail(emailCliente);
                    String nro = String.format("%06d", pedido.getIdPedido());
                    emailService.sendBoletaPresencialEmail(emailCliente, nombreCliente, pedido, detalles, nro);
                } catch (Exception e) {
                    System.out.println("Error enviando boleta: " + e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("ok", true);
            response.put("idPedido", pedido.getIdPedido());
            response.put("total", pedido.getTotal());
            response.put("nroBoleta", String.format("%06d", pedido.getIdPedido()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
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
                    if (est.contains("COMPLETAD") || est.contains("ENTREGAD") || est.contains("CANCELAD") || est.contains("RECHAZAD")) return false;
                    // Excluir ventas presenciales (solo pedidos de clientes: delivery y recojo)
                    if ("Venta en tienda".equalsIgnoreCase(p.getDireccionEnvio())) return false;
                    return true;
                })
                .sorted((a, b) -> b.getIdPedido().compareTo(a.getIdPedido()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(pedidos);
    }

    /**
     * Marca un pedido como entregado.
     */
    @PutMapping("/pedidos/{id}/entregar")
    public ResponseEntity<?> marcarEntregado(@PathVariable Integer id) {
        Optional<Pedido> pedidoOpt = pedidoRepository.findById(id);
        if (pedidoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pedido no encontrado"));
        }
        Pedido pedido = pedidoOpt.get();
        String est = pedido.getEstado() != null ? pedido.getEstado().toUpperCase() : "";
        if (est.contains("COMPLETAD") || est.contains("ENTREGAD")) {
            return ResponseEntity.ok(Map.of("ok", true, "estado", "COMPLETADO", "mensaje", "Ya estaba entregado"));
        }
        pedido.setEstado("COMPLETADO");
        pedidoRepository.save(pedido);

        // Enviar email de confirmación de entrega al cliente
        try {
            User u = pedido.getUsuario();
            if (u != null && u.getEmail() != null && !u.getEmail().isBlank()) {
                String nro = pedido.getIdPedido() != null ? String.format("%06d", pedido.getIdPedido()) : "";
                emailService.sendEntregadoEmail(u.getEmail(), u.getNombre(), pedido, nro);
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(Map.of("ok", true, "estado", "COMPLETADO"));
    }

    /**
     * Lista de pedidos ya entregados (COMPLETADO/ENTREGADO).
     */
    @GetMapping("/pedidos/entregados")
    public ResponseEntity<?> listarPedidosEntregados() {
        List<Pedido> pedidos = pedidoRepository.findAll().stream()
                .filter(p -> {
                    String est = p.getEstado() != null ? p.getEstado().toUpperCase() : "";
                    return est.contains("COMPLETAD") || est.contains("ENTREGAD");
                })
                .sorted((a, b) -> b.getIdPedido().compareTo(a.getIdPedido()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(pedidos);
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
