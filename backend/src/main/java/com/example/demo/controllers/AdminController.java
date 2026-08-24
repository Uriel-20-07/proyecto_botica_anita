package com.example.demo.controllers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.models.Administrador;
import com.example.demo.models.DetallePedido;
import com.example.demo.models.InventarioLote;
import com.example.demo.models.Pedido;
import com.example.demo.models.Producto;
import com.example.demo.models.User;
import com.example.demo.models.Cupon;
import com.example.demo.repositories.AdministradorRepository;
import com.example.demo.repositories.DetallePedidoRepository;
import com.example.demo.repositories.InventarioLoteRepository;
import com.example.demo.repositories.PedidoRepository;
import com.example.demo.repositories.ProductoRepository;
import com.example.demo.repositories.UserRepository;
import com.example.demo.repositories.CuponRepository;
import com.example.demo.services.JwtService;
import com.example.demo.services.EmailService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdministradorRepository administradorRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private DetallePedidoRepository detallePedidoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CuponRepository cuponRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtService jwtService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El correo y contraseña son requeridos"));
        }

        Optional<Administrador> adminOpt = administradorRepository.findByCorreoCorp(email);
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales corporativas inválidas"));
        }

        Administrador admin = adminOpt.get();
        if (!passwordEncoder.matches(password, admin.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales corporativas inválidas"));
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

    @GetMapping("/productos")
    public ResponseEntity<?> listarProductos() {
        return ResponseEntity.ok(productoRepository.findAll());
    }

    @PutMapping("/productos/{id}/stock")
    public ResponseEntity<?> actualizarStock(@PathVariable Integer id, @RequestBody Map<String, Integer> request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<Administrador> adminOpt = administradorRepository.findByCorreoCorp(email);

        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No autorizado"));
        }

        Administrador admin = adminOpt.get();
        if (!"admin".equalsIgnoreCase(admin.getRol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Acceso denegado: Solo el administrador puede modificar el stock"));
        }

        Integer nuevoStock = request.get("stock");
        if (nuevoStock == null || nuevoStock < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "El stock debe ser un número entero no negativo"));
        }

        Optional<Producto> prodOpt = productoRepository.findById(id);
        if (prodOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Producto no encontrado"));
        }

        Producto producto = prodOpt.get();
        producto.setStock(nuevoStock);
        Producto guardado = productoRepository.save(producto);

        return ResponseEntity.ok(guardado);
    }

    @PutMapping("/productos/{id}/oferta")
    public ResponseEntity<?> actualizarOferta(@PathVariable Integer id, @RequestBody Map<String, Object> request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<Administrador> adminOpt = administradorRepository.findByCorreoCorp(email);

        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No autorizado"));
        }

        Administrador admin = adminOpt.get();
        if (!"admin".equalsIgnoreCase(admin.getRol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Acceso denegado: Solo el administrador puede modificar ofertas"));
        }

        Optional<Producto> prodOpt2 = productoRepository.findById(id);
        if (prodOpt2.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Producto no encontrado"));
        }

        Producto producto2 = prodOpt2.get();
        Boolean enOferta = (Boolean) request.get("enOferta");
        Object precioOfertaRaw = request.get("precioOferta");

        if (enOferta == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El campo enOferta es obligatorio"));
        }

        if (Boolean.TRUE.equals(enOferta)) {
            if (precioOfertaRaw == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Se requiere precioOferta cuando enOferta es true"));
            }
            BigDecimal precioOferta;
            try {
                precioOferta = new BigDecimal(precioOfertaRaw.toString());
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "precioOferta debe ser un número válido"));
            }
            if (precioOferta.compareTo(producto2.getPrecioVenta()) >= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "El precio de oferta debe ser menor al precio de venta"));
            }
            producto2.setPrecioOferta(precioOferta);
            producto2.setEnOferta(true);
        } else {
            producto2.setEnOferta(false);
            producto2.setPrecioOferta(null);
        }

        Producto guardado2 = productoRepository.save(producto2);
        return ResponseEntity.ok(guardado2);
    }


    @GetMapping("/ventas")
    public ResponseEntity<?> listarVentas() {
        List<Pedido> pedidos = pedidoRepository.findAllByOrderByFechaDesc();
        List<Integer> pedidoIds = pedidos.stream().map(Pedido::getIdPedido).collect(Collectors.toList());

        List<DetallePedido> todosLosDetalles = pedidoIds.isEmpty() ? new ArrayList<>() : detallePedidoRepository.findByPedido_IdPedidoIn(pedidoIds);

        Map<Integer, List<DetallePedido>> detallesPorPedido = todosLosDetalles.stream()
                .filter(d -> d.getPedido() != null)
                .collect(Collectors.groupingBy(d -> d.getPedido().getIdPedido()));

        List<Map<String, Object>> response = pedidos.stream().map(pedido -> {
            Map<String, Object> map = new HashMap<>();
            map.put("idPedido", pedido.getIdPedido());
            map.put("fecha", pedido.getFecha());
            map.put("estado", pedido.getEstado());
            map.put("total", pedido.getTotal());
            map.put("metodoPago", pedido.getMetodoPago());
            map.put("direccionEnvio", pedido.getDireccionEnvio());
            map.put("distrito", pedido.getDistrito());
            map.put("usuario", Map.of(
                    "nombre", pedido.getUsuario().getNombre(),
                    "apellido", pedido.getUsuario().getApellido(),
                    "email", pedido.getUsuario().getEmail()));

            List<DetallePedido> detalles = detallesPorPedido.getOrDefault(pedido.getIdPedido(), new ArrayList<>());

            List<Map<String, Object>> detallesMap = detalles.stream().map(d -> {
                Map<String, Object> dm = new HashMap<>();
                dm.put("idDetallePedido", d.getIdDetallePedido());
                dm.put("producto", Map.of(
                        "idProducto", d.getProducto().getIdProducto(),
                        "nombre", d.getProducto().getNombre(),
                        "precioVenta", d.getProducto().getPrecioVenta()));
                dm.put("cantidad", d.getCantidad());
                dm.put("precioHistorico", d.getPrecioHistorico());
                return dm;
            }).collect(Collectors.toList());

            map.put("detalles", detallesMap);
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ventas/stats")
    public ResponseEntity<?> obtenerStats() {
        List<Pedido> pedidos = pedidoRepository.findAll();

        Map<String, BigDecimal> dailySum = pedidos.stream()
                .filter(p -> p.getFecha() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getFecha().toLocalDate().toString(),
                        Collectors.reducing(BigDecimal.ZERO, Pedido::getTotal, BigDecimal::add)));

        Map<String, Long> dailyCount = pedidos.stream()
                .filter(p -> p.getFecha() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getFecha().toLocalDate().toString(),
                        Collectors.counting()));

        List<String> sortedDates = dailySum.keySet().stream().sorted().collect(Collectors.toList());

        List<Map<String, Object>> stats = new ArrayList<>();
        for (String date : sortedDates) {
            Map<String, Object> statItem = new HashMap<>();
            statItem.put("fecha", date);
            statItem.put("totalVentas", dailySum.get(date));
            statItem.put("cantidadPedidos", dailyCount.get(date));
            stats.add(statItem);
        }

        return ResponseEntity.ok(stats);
    }

    @Autowired
    private InventarioLoteRepository loteRepository;

    @GetMapping("/productos/{id}/lotes")
    public ResponseEntity<?> getLotesPorProducto(@PathVariable Integer id) {
        return ResponseEntity.ok(loteRepository.findByProductoIdProductoOrderByFechaVencimientoAsc(id));
    }

    @PostMapping("/productos/{id}/lotes")
    public ResponseEntity<?> agregarLote(@PathVariable Integer id, @RequestBody InventarioLote nuevoLote) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        nuevoLote.setProducto(producto);
        nuevoLote.setFechaIngreso(LocalDate.now());
        loteRepository.save(nuevoLote);

        return ResponseEntity.ok(nuevoLote);
    }

    @DeleteMapping("/productos/lotes/{idLote}")
    public ResponseEntity<?> eliminarLote(@PathVariable Integer idLote) {
        loteRepository.deleteById(idLote);
        return ResponseEntity.ok().build();
    }

    // ====================================================================
    // PREDICCIONES Y TOP VENTAS
    // ====================================================================
    @GetMapping("/ventas/top-productos")
    public ResponseEntity<?> obtenerTopProductos(@RequestParam(defaultValue = "mes") String rango) {
        List<Pedido> pedidos = pedidoRepository.findAll();
        LocalDateTime fechaLimite = LocalDateTime.now().minusMonths(1);

        if ("semana".equalsIgnoreCase(rango)) {
            fechaLimite = LocalDateTime.now().minusDays(7);
        }

        final LocalDateTime limiteFinal = fechaLimite;

        Set<Integer> pedidosValidosIds = pedidos.stream()
                .filter(p -> p.getFecha() != null && p.getFecha().isAfter(limiteFinal))
                .map(Pedido::getIdPedido)
                .collect(Collectors.toSet());

        List<DetallePedido> detalles = pedidosValidosIds.isEmpty() ? new ArrayList<>() : detallePedidoRepository.findByPedido_IdPedidoIn(pedidosValidosIds);
        Map<String, Integer> conteoPorProducto = detalles.stream()
                .filter(d -> d.getProducto() != null)
                .collect(Collectors.groupingBy(
                        d -> d.getProducto().getNombre(),
                        Collectors.summingInt(DetallePedido::getCantidad)
                ));

        List<Map<String, Object>> top = conteoPorProducto.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("nombre", e.getKey());
                    map.put("cantidad", e.getValue());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(top);
    }

    // ====================================================================
    // GESTIÓN DE USUARIOS Y CUPONES
    // ====================================================================
    @GetMapping("/usuarios")
    public ResponseEntity<?> listarUsuarios() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<Administrador> adminOpt = administradorRepository.findByCorreoCorp(email);

        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No autorizado"));
        }

        List<User> usuarios = userRepository.findAll();
        // Ocultar contraseñas por seguridad
        usuarios.forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(usuarios);
    }

    @PostMapping("/cupones/lanzar")
    public ResponseEntity<?> lanzarCupon(@RequestBody Map<String, Object> request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<Administrador> adminOpt = administradorRepository.findByCorreoCorp(email);

        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No autorizado"));
        }

        Administrador admin = adminOpt.get();
        if (!"admin".equalsIgnoreCase(admin.getRol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Acceso denegado: Solo el administrador puede lanzar cupones"));
        }

        String codigoPrefix = (String) request.get("codigoPrefix");
        if (codigoPrefix == null || codigoPrefix.trim().isEmpty()) {
            codigoPrefix = "PROMO";
        }
        codigoPrefix = codigoPrefix.trim().toUpperCase();

        Object valorDescuentoRaw = request.get("valorDescuento");
        if (valorDescuentoRaw == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El valor del descuento es obligatorio"));
        }
        BigDecimal valorDescuento = new BigDecimal(valorDescuentoRaw.toString());

        String descripcion = (String) request.get("descripcion");
        if (descripcion == null || descripcion.trim().isEmpty()) {
            descripcion = "Cupón de campaña de descuento";
        }

        Integer diasVigencia = 15;
        if (request.get("diasVigencia") != null) {
            diasVigencia = Integer.parseInt(request.get("diasVigencia").toString());
        }

        String tipoEnvio = (String) request.get("tipoEnvio");
        if (tipoEnvio == null || tipoEnvio.trim().isEmpty()) {
            tipoEnvio = "TODOS";
        }

        List<User> destinatarios = new ArrayList<>();
        if ("SELECCIONADO".equalsIgnoreCase(tipoEnvio)) {
            Object idUsuarioRaw = request.get("idUsuario");
            if (idUsuarioRaw == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Debe proporcionar idUsuario para envío individual"));
            }
            Integer idUsuario = Integer.parseInt(idUsuarioRaw.toString());
            Optional<User> userOpt = userRepository.findById(idUsuario);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado"));
            }
            destinatarios.add(userOpt.get());
        } else {
            destinatarios = userRepository.findAll().stream().filter(User::getActivo).collect(Collectors.toList());
        }

        if (destinatarios.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No hay destinatarios activos para el envío"));
        }

        // Generar cupones
        List<Cupon> cuponesGuardados = new ArrayList<>();
        for (User user : destinatarios) {
            Cupon cupon = new Cupon();
            // Generar código único: PREFIX-XXXXXX
            String uniqueCode = codigoPrefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            cupon.setCodigo(uniqueCode);
            cupon.setUsuario(user);
            cupon.setTipoDescuento("PORCENTAJE");
            cupon.setValorDescuento(valorDescuento);
            cupon.setFechaCreacion(LocalDateTime.now());
            cupon.setFechaExpiracion(LocalDateTime.now().plusDays(diasVigencia));
            cupon.setUsado(false);
            cupon.setDescripcion(descripcion);
            cupon.setActivo(true);
            cuponesGuardados.add(cuponRepository.save(cupon));
        }

        // Enviar correos electrónicos de forma asíncrona
        final List<Cupon> finalCupones = cuponesGuardados;
        final double finalDescuento = valorDescuento.doubleValue();
        final String finalDesc = descripcion;
        CompletableFuture.runAsync(() -> {
            for (Cupon c : finalCupones) {
                emailService.sendCampaignCouponEmail(
                    c.getUsuario().getEmail(),
                    c.getUsuario().getNombre(),
                    c.getCodigo(),
                    finalDescuento,
                    finalDesc
                );
            }
        });

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Campañas de cupones lanzada con éxito",
            "cuponesEmitidos", cuponesGuardados.size()
        ));
    }
}
