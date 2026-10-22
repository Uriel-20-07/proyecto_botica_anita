package com.example.demo.services;

import com.example.demo.dto.PagoRequest;
import com.example.demo.models.*;
import com.example.demo.repositories.*;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

@Service
public class PagoService {

    @Autowired
    private CarritoService carritoService;
    @Autowired
    private CuponRepository cuponRepository;
    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private DetallePedidoRepository detallePedidoRepository;
    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private EmailService emailService;
    // INYECTAMOS EL REPOSITORIO DE LOTES
    @Autowired
    private InventarioLoteRepository loteRepository;

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    // Datos de la cuenta Plin de la botica (se configuran en application.properties / variables de entorno)
    @Value("${plin.numero:}")
    private String plinNumero;
    @Value("${plin.titular:}")
    private String plinTitular;

    // Datos de la cuenta SIP (antes Ágora) de la botica
    @Value("${sip.numero:}")
    private String sipNumero;
    @Value("${sip.titular:}")
    private String sipTitular;

    // Tienda Botica Anita: Av. Los Jazmines 408, Independencia (pin verificado)
    private static final double TIENDA_LAT = -12.001181821042714;
    private static final double TIENDA_LNG = -77.04736614959307;
    // Radio de cobertura de delivery en km
    private static final double RADIO_COBERTURA_KM = 2.0;

    @Autowired
    private GeocodificacionService geocodificacionService;

    // Debe reflejar el mismo valor que 'mostrarRecetas' en environment.ts del frontend.
    // Si el flujo de recetas está oculto en la UI, el backend NO debe exigir receta,
    // o el pedido queda atascado en EN_ESPERA sin forma de resolverse.
    @Value("${app.recetas.habilitadas:false}")
    private boolean recetasHabilitadas;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    /**
     * Valida que la dirección de entrega esté dentro del radio de cobertura desde la tienda.
     * Lanza RuntimeException si está fuera de cobertura.
     */
    private void validarCobertura(String direccion, String distrito) {
        if (direccion == null || direccion.isBlank()) return;
        geocodificacionService.esperarTurno();
        double[] coords = geocodificacionService.buscar(direccion, distrito);
        if (coords == null) {
            // No se pudo ubicar la dirección (o Nominatim está saturado):
            // no se bloquea el pago, el frontend ya avisa si no encuentra la dirección.
            return;
        }
        double distM = distanciaM(TIENDA_LAT, TIENDA_LNG, coords[0], coords[1]);
        double distKm = distM / 1000.0;
        if (distKm > RADIO_COBERTURA_KM) {
            throw new RuntimeException("Tu dirección está fuera del área de cobertura de delivery (máx. "
                    + RADIO_COBERTURA_KM + " km desde la botica).");
        }
    }

    private double distanciaM(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    /**
     * LÓGICA FEFO: Descuenta el stock de los lotes que vencen primero.
     */
    private void descontarStockLotesFefo(Integer idProducto, int cantidadComprada) {
        List<InventarioLote> lotes = loteRepository.findByProductoIdProductoOrderByFechaVencimientoAsc(idProducto);
        int cantidadRestante = cantidadComprada;

        if (lotes.isEmpty()) {
            // El producto no tiene lotes físicos registrados:
            // se descuenta directamente del stock general del producto.
            Producto producto = productoRepository.findById(idProducto)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + idProducto));
            int stockActual = producto.getStock() != null ? producto.getStock() : 0;
            if (stockActual < cantidadComprada) {
                throw new RuntimeException("Stock insuficiente para el producto: " + producto.getNombre());
            }
            producto.setStock(stockActual - cantidadComprada);
            productoRepository.save(producto);
            return;
        }

        // Lógica FEFO: descontar de los lotes que vencen primero
        for (InventarioLote lote : lotes) {
            if (cantidadRestante <= 0)
                break;
            if (lote.getCantidadActual() <= 0)
                continue;

            if (lote.getCantidadActual() >= cantidadRestante) {
                lote.setCantidadActual(lote.getCantidadActual() - cantidadRestante);
                loteRepository.save(lote);
                cantidadRestante = 0;
            } else {
                cantidadRestante -= lote.getCantidadActual();
                lote.setCantidadActual(0);
                loteRepository.save(lote);
            }
        }

        if (cantidadRestante > 0) {
            throw new RuntimeException("Stock insuficiente en lotes físicos para el producto ID: " + idProducto);
        }
    }

    public PaymentIntent crearPaymentIntent(Integer idUsuario, PagoRequest request) throws Exception {
        Carrito carrito = carritoService.obtenerOCrearCarrito(idUsuario);
        if (carrito.getDetalles().isEmpty())
            throw new RuntimeException("Carrito vacío.");

        // La cobertura de 2 km solo aplica al delivery (envío a domicilio).
        // El recojo en tienda es gratis y no tiene radio de entrega.
        if (request.isEsUrgente()) {
            validarCobertura(request.getDireccionEnvio(), request.getDistrito());
        }

        double subtotal = 0.0;
        for (DetalleCarrito d : carrito.getDetalles()) {
            subtotal += obtenerPrecioConDescuento(d.getProducto()).doubleValue() * d.getCantidad();
        }

        if (request.getCodigoCupon() != null && !request.getCodigoCupon().trim().isEmpty()) {
            Cupon cupon = cuponRepository.findByCodigo(request.getCodigoCupon().trim().toUpperCase())
                    .orElseThrow(() -> new RuntimeException("Cupón inválido."));
            if (cupon.getActivo() && !cupon.getUsado() && cupon.getFechaExpiracion().isAfter(LocalDateTime.now())) {
                double descuento = subtotal * (cupon.getValorDescuento().doubleValue() / 100.0);
                subtotal -= descuento;
            }
        }

        double costoEnvio = request.isEsUrgente() ? 5.0 : 0.0;
        long amountInCents = Math.round((subtotal + costoEnvio) * 100);
        String moneda = (request.getMoneda() != null) ? request.getMoneda() : "pen";

        return PaymentIntent.create(PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(moneda)
                .build());
    }

    /** Datos que el cliente necesita para pagar por Plin. */
    public Map<String, String> obtenerInfoPlin() {
        return Map.of("numero", plinNumero, "titular", plinTitular);
    }

    /** Datos que el cliente necesita para pagar por SIP (antes Ágora). */
    public Map<String, String> obtenerInfoSip() {
        return Map.of("numero", sipNumero, "titular", sipTitular);
    }

    @Transactional
    public void procesarTransaccion(Integer idUsuario, PagoRequest request) {
        Carrito carrito = carritoService.obtenerOCrearCarrito(idUsuario);
        if (carrito.getDetalles().isEmpty())
            throw new RuntimeException("Carrito vacío.");

        // La cobertura de 2 km solo aplica al delivery (envío a domicilio).
        // El recojo en tienda es gratis y no tiene radio de entrega.
        if (request.isEsUrgente()) {
            validarCobertura(request.getDireccionEnvio(), request.getDistrito());
        }

        // --- Validación de pagos por transferencia: Plin y SIP (antes de consumir cupones o stock) ---
        boolean esTransferencia = "PLIN".equalsIgnoreCase(request.getMetodoPago())
                || "SIP".equalsIgnoreCase(request.getMetodoPago());
        String numeroOperacion = null;
        if (esTransferencia) {
            numeroOperacion = request.getNumeroOperacion() == null ? "" : request.getNumeroOperacion().trim();
            if (!numeroOperacion.matches("\\d{6,12}")) {
                throw new RuntimeException("Ingresa un número de operación válido (6 a 12 dígitos).");
            }
            if (pedidoRepository.existsByNumeroOperacion(numeroOperacion)) {
                throw new RuntimeException("Este número de operación ya fue registrado en otro pedido.");
            }
        }

        double subtotal = 0.0;
        for (DetalleCarrito d : carrito.getDetalles()) {
            subtotal += obtenerPrecioConDescuento(d.getProducto()).doubleValue() * d.getCantidad();
        }
        BigDecimal totalFinal = BigDecimal.valueOf(subtotal);

        if (request.getCodigoCupon() != null && !request.getCodigoCupon().trim().isEmpty()) {
            Cupon cupon = cuponRepository.findByCodigo(request.getCodigoCupon().trim().toUpperCase())
                    .orElseThrow(() -> new RuntimeException("Cupón inválido."));
            if (!cupon.getActivo() || cupon.getUsado() || cupon.getFechaExpiracion().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("Cupón expirado o usado.");
            }
            double descuento = subtotal * (cupon.getValorDescuento().doubleValue() / 100.0);
            totalFinal = BigDecimal.valueOf(subtotal - descuento);
            cupon.setUsado(true);
            cupon.setFechaUso(LocalDateTime.now());
            cuponRepository.save(cupon);
        }

        double costoEnvio = request.isEsUrgente() ? 5.0 : 0.0;
        totalFinal = totalFinal.add(BigDecimal.valueOf(costoEnvio));

        boolean requiereReceta = false;
        if (recetasHabilitadas) {
            for (DetalleCarrito detCart : carrito.getDetalles()) {
                String nombre = detCart.getProducto().getNombre().toLowerCase();
                if (nombre.contains("clonazepam") || nombre.contains("losartan") || nombre.contains("losartán")) {
                    requiereReceta = true;
                    break;
                }
            }
        }

        Pedido pedido = new Pedido();
        pedido.setUsuario(carrito.getUsuario());
        // Se guarda en UTC: el servidor puede estar en otra zona horaria (Azure usa UTC)
        // y el frontend siempre interpreta la fecha como UTC.
        pedido.setFecha(LocalDateTime.now(ZoneOffset.UTC));
        // Plin automático: sin verificación manual, marca PAGADO directo (igual que Yape)
        pedido.setEstado(requiereReceta ? "EN_ESPERA" : "PAGADO");
        pedido.setNumeroOperacion(numeroOperacion);
        pedido.setTotal(totalFinal);
        pedido.setDireccionEnvio(request.getDireccionEnvio());
        pedido.setEsUrgente(request.isEsUrgente());
        pedido.setDistrito(request.getDistrito());
        pedido.setMetodoPago(request.getMetodoPago());
        pedido.setIdReceta(request.getIdReceta());
        pedidoRepository.save(pedido); // Guarda el objeto
        // Como el objeto 'pedido' ya existe en memoria, ya tiene el ID asignado
        // y puedes seguir usándolo sin necesidad de re-asignarlo.

        List<DetallePedido> detallesGuardados = new ArrayList<>();
        for (DetalleCarrito detCart : carrito.getDetalles()) {
            Producto producto = detCart.getProducto();
            int cantidadComprada = detCart.getCantidad();

            // EJECUTAMOS LA LÓGICA FEFO (Esto actualiza los lotes y el trigger actualizará
            // el stock general)
            descontarStockLotesFefo(producto.getIdProducto(), cantidadComprada);

            DetallePedido detPed = new DetallePedido();
            detPed.setPedido(pedido);
            detPed.setProducto(producto);
            detPed.setCantidad(cantidadComprada);
            detPed.setPrecioHistorico(obtenerPrecioConDescuento(producto));
            detPed = detallePedidoRepository.save(detPed);
            detallesGuardados.add(detPed);
        }

        carritoService.vaciarCarrito(idUsuario);

        // Envío de correo...
        User usuario = carrito.getUsuario();
        long nroPedidoCliente = pedidoRepository.countByUsuario(usuario);
        String nroBoleta = String.format("%06d", nroPedidoCliente);

        // Correo de confirmación para todos los métodos (Plin ya no espera verificación)
        enviarCorreoConfirmacion(usuario, pedido, detallesGuardados, request.getCodigoCupon(), nroBoleta);
    }

    private void enviarCorreoConfirmacion(User usuario, Pedido pedido, List<DetallePedido> detalles,
            String codigoCupon, String nroBoleta) {
        CompletableFuture.runAsync(() -> {
            try {
                emailService.sendOrderConfirmationEmail(usuario.getEmail(),
                        usuario.getNombre() + " " + usuario.getApellido(), pedido, detalles,
                        codigoCupon, nroBoleta);
            } catch (Exception e) {
                System.out.println("Error en correo: " + e.getMessage());
            }
        });
    }

    public Map<String, Object> validarCupon(Integer idUsuario, String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            throw new RuntimeException("Código de cupón vacío.");
        }
        
        Cupon cupon = cuponRepository.findByCodigo(codigo.trim().toUpperCase())
                .orElseThrow(() -> new RuntimeException("Cupón inválido o inexistente."));
                
        if (!cupon.getUsuario().getId().equals(idUsuario)) {
            throw new RuntimeException("Este cupón no pertenece a tu cuenta.");
        }
        
        if (!cupon.getActivo()) {
            throw new RuntimeException("El cupón no está activo.");
        }
        
        if (cupon.getUsado()) {
            throw new RuntimeException("El cupón ya fue usado.");
        }
        
        if (cupon.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El cupón ha expirado.");
        }
        
        return Map.of(
            "valido", true,
            "valorDescuento", cupon.getValorDescuento(),
            "tipoDescuento", cupon.getTipoDescuento(),
            "descripcion", cupon.getDescripcion()
        );
    }

    public BigDecimal obtenerPrecioConDescuento(Producto producto) {
        if (producto.getPrecioConDescuento() != null) {
            return producto.getPrecioConDescuento();
        }
        return producto.getPrecioVenta();
    }
}
