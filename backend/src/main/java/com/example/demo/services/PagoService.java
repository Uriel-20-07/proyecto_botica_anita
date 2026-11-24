package com.example.demo.services;

import com.example.demo.dto.PagoRequest;
import com.example.demo.dto.PedidoEmailDTO;
import com.example.demo.exceptions.CarritoException;
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

import java.math.RoundingMode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@Service
public class PagoService {

    @Autowired
    private CarritoService carritoService;
    @Autowired
    private StockService stockService;
    @Autowired
    private PromocionService promocionService;
    @Autowired
    private CuponRepository cuponRepository;
    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private DetallePedidoRepository detallePedidoRepository;
    @Autowired
    private EmailService emailService;
    @Autowired
    private DetalleCarritoRepository detalleCarritoRepository;

    @Value("${app.carrito.reserva.horas:24}")
    private long horasReserva;

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
    private static final double TIENDA_LAT = -12.001353207277829;
    private static final double TIENDA_LNG = -77.04735384172724;
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
     * Devuelve las coordenadas resueltas [lat, lng] para reutilizarlas al crear el
     * pedido (así se evita geocodificar dos veces la misma dirección).
     * Lanza RuntimeException si está fuera de cobertura.
     */
    private double[] validarCobertura(String direccion, String distrito, Double lat, Double lng) {
        double latU;
        double lngU;
        if (lat != null && lng != null) {
            latU = lat;
            lngU = lng;
        } else {
            if (direccion == null || direccion.isBlank()) {
                throw new RuntimeException("Ingresa la direccion de entrega.");
            }
            geocodificacionService.esperarTurno();
            double[] coords = geocodificacionService.buscar(direccion, distrito);
            if (coords == null) {
                throw new RuntimeException("No se pudo verificar la direccion de entrega. Selecciona una sugerencia del autocompletado.");
                // No se pudo ubicar la dirección (o Nominatim está saturado):
                // no se bloquea el pago, el frontend ya avisa si no encuentra la dirección.
            }
            latU = coords[0];
            lngU = coords[1];
        }
        double distM = distanciaM(TIENDA_LAT, TIENDA_LNG, latU, lngU);
        double distKm = distM / 1000.0;
        if (distKm > RADIO_COBERTURA_KM) {
            throw new RuntimeException("Tu dirección está fuera del área de cobertura de delivery (máx. "
                    + RADIO_COBERTURA_KM + " km desde la botica).");
        }
        return new double[] { latU, lngU };
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

    public PaymentIntent crearPaymentIntent(Integer idUsuario, PagoRequest request) throws Exception {
        Carrito carrito = carritoService.obtenerOCrearCarrito(idUsuario);
        if (carrito.getDetalles().isEmpty())
            throw new RuntimeException("Carrito vacío.");

        // La cobertura de 2 km solo aplica al delivery (envío a domicilio).
        // El recojo en tienda es gratis y no tiene radio de entrega.
        if (request.isEsUrgente()) {
            // Solo se entrega en el distrito de Independencia.
            if (request.getDistrito() != null && !request.getDistrito().trim().equalsIgnoreCase("Independencia")) {
                throw new RuntimeException("Solo realizamos delivery en el distrito de Independencia.");
            }
            validarCobertura(request.getDireccionEnvio(), request.getDistrito(), request.getLatitudEntrega(), request.getLongitudEntrega());
        }

        double subtotal = 0.0;
        for (DetalleCarrito d : carrito.getDetalles()) {
            subtotal += obtenerPrecioConDescuento(d.getProducto()).doubleValue() * d.getCantidad();
        }

        // Promociones (3x2, combos, etc.): se derivan de lo que hay en el carrito
        subtotal -= promocionService.calcularDescuento(carrito).total().doubleValue();

        if (request.getCodigoCupon() != null
        && !request.getCodigoCupon().trim().isEmpty()) {

    Cupon cupon = obtenerCuponValido(
            idUsuario, request.getCodigoCupon());

    BigDecimal descuento = calcularDescuentoCupon(carrito, cupon);

    subtotal -= descuento.doubleValue();
}

        double costoEnvio = request.isEsUrgente() ? 5.0 : 0.0;
        long amountInCents = Math.round((subtotal + costoEnvio) * 100);
        String moneda = "mxn"; // Forzamos la moneda nativa de tu cuenta de Stripe

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

        // Validar reservas: si la línea ya fue marcada EXPIRADA (stock liberado),
        // se intenta reservar de nuevo. Si sigue ACTIVA pero pasó la hora, la
        // reserva todavía está contada en stockEnCarrito: solo se extiende.
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        for (DetalleCarrito d : carrito.getDetalles()) {
            boolean vencida = d.getFechaExpiracion() == null
                    || !d.getFechaExpiracion().isAfter(ahora);

            if (d.getEstado() == EstadoDetalleCarrito.EXPIRADA) {
                try {
                    stockService.reservar(d.getProducto().getIdProducto(), d.getCantidad());
                    d.setEstado(EstadoDetalleCarrito.ACTIVA);
                    d.setFechaExpiracion(ahora.plusHours(horasReserva));
                    detalleCarritoRepository.save(d);
                } catch (CarritoException e) {
                    throw new RuntimeException("La reserva de \"" + d.getProducto().getNombre()
                            + "\" venció y ya no hay stock disponible. Vuelve a agregarlo al carrito.");
                }
            } else if (d.getEstado() != EstadoDetalleCarrito.ACTIVA) {
                throw new RuntimeException("La reserva de \"" + d.getProducto().getNombre()
                        + "\" ya no está activa. Vuelve a agregarlo al carrito.");
            } else if (vencida) {
                // La reserva sigue vigente en stockEnCarrito: se extiende para que
                // el scheduler no la libere mientras se confirma el pago.
                d.setFechaExpiracion(ahora.plusHours(horasReserva));
                detalleCarritoRepository.save(d);
            }

            if (d.getProducto().isAgotado()) {
                throw new RuntimeException("El producto \"" + d.getProducto().getNombre()
                        + "\" está agotado por ahora. Elimínalo del carrito para continuar.");
            }
        }

        // La cobertura de 2 km solo aplica al delivery (envío a domicilio).
        // El recojo en tienda es gratis y no tiene radio de entrega.
        double[] coordsEntrega = null;
        if (request.isEsUrgente()) {
            // Solo se entrega en el distrito de Independencia.
            if (request.getDistrito() != null && !request.getDistrito().trim().equalsIgnoreCase("Independencia")) {
                throw new RuntimeException("Solo realizamos delivery en el distrito de Independencia.");
            }
            // Devuelve las coords ya resueltas para no geocodificar otra vez al crear el pedido.
            coordsEntrega = validarCobertura(request.getDireccionEnvio(), request.getDistrito(), request.getLatitudEntrega(), request.getLongitudEntrega());
        }

        // --- Validación de pagos por transferencia: Plin, SIP y Yape con QR (antes de consumir cupones o stock) ---
        // Yape con QR se distingue del flujo con código de aprobación porque envía número de operación.
        boolean esYapeQr = "YAPE".equalsIgnoreCase(request.getMetodoPago())
                && request.getNumeroOperacion() != null && !request.getNumeroOperacion().trim().isEmpty();
        boolean esTransferencia = "PLIN".equalsIgnoreCase(request.getMetodoPago())
                || "SIP".equalsIgnoreCase(request.getMetodoPago())
                || esYapeQr;
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
        BigDecimal totalFinal = BigDecimal.valueOf(subtotal)
                .subtract(promocionService.calcularDescuento(carrito).total());

        if (request.getCodigoCupon() != null
        && !request.getCodigoCupon().trim().isEmpty()) {

    Cupon cupon = obtenerCuponValido(
            idUsuario, request.getCodigoCupon());

    BigDecimal descuento = calcularDescuentoCupon(carrito, cupon);

    totalFinal = totalFinal.subtract(descuento);

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
        // Coordenadas de entrega ya resueltas por validarCobertura (o enviadas por
        // el frontend). En recojo no se geocodifica nada: el mapa no aplica.
        if (coordsEntrega != null) {
            pedido.setLatitudEntrega(BigDecimal.valueOf(coordsEntrega[0]));
            pedido.setLongitudEntrega(BigDecimal.valueOf(coordsEntrega[1]));
        }
        pedido.setMetodoPago(request.getMetodoPago());
        pedido.setIdReceta(request.getIdReceta());
        pedidoRepository.save(pedido); // Guarda el objeto
        // Como el objeto 'pedido' ya existe en memoria, ya tiene el ID asignado
        // y puedes seguir usándolo sin necesidad de re-asignarlo.

        List<DetallePedido> detallesGuardados = new ArrayList<>();
        for (DetalleCarrito detCart : carrito.getDetalles()) {
            Producto producto = detCart.getProducto();
            int cantidadComprada = detCart.getCantidad();

            // Confirma la reserva y descuenta el stock físico (FEFO) una sola vez.
            stockService.confirmarVenta(producto, cantidadComprada);

            DetallePedido detPed = new DetallePedido();
            detPed.setPedido(pedido);
            detPed.setProducto(producto);
            detPed.setCantidad(cantidadComprada);
            detPed.setPrecioHistorico(obtenerPrecioConDescuento(producto));
            detallesGuardados.add(detPed);
        }
        // Un solo guardado por lote de filas en vez de un INSERT por producto.
        detallesGuardados = detallePedidoRepository.saveAll(detallesGuardados);

        // Las reservas ya se descontaron en StockService.confirmarVenta:
        // vaciar sin volver a liberar stock.
        carritoService.vaciarCarritoSinLiberar(idUsuario);

        // Envío de correo...
        User usuario = carrito.getUsuario();
        long nroPedidoCliente = pedidoRepository.countByUsuario(usuario);
        String nroBoleta = String.format("%06d", nroPedidoCliente);

        // Extraemos los datos planos DENTRO de la transacción: el hilo asíncrono
        // nunca toca entidades JPA ni el EntityManager (evita LazyInitializationException).
        PedidoEmailDTO emailDto = PedidoEmailDTO.from(
                pedido, usuario, detallesGuardados, request.getCodigoCupon(), nroBoleta);

        // El pedido y el stock ya quedaron guardados de forma síncrona.
        // El correo se dispara después del commit y en otro hilo, así la
        // respuesta al cliente no espera al SMTP.
        enviarCorreoConfirmacion(emailDto);
    }

    private void enviarCorreoConfirmacion(PedidoEmailDTO emailDto) {
        AfterCommitRunner.run(() -> emailService.sendOrderConfirmationEmail(emailDto));
    }
private Cupon obtenerCuponValido(Integer idUsuario, String codigo) {
    Cupon cupon = cuponRepository
            .findByCodigo(codigo.trim().toUpperCase())
            .orElseThrow(() -> new RuntimeException("Cupón inválido."));

    if (cupon.getUsuario() == null
            || !cupon.getUsuario().getId().equals(idUsuario)) {
        throw new RuntimeException("Este cupón no pertenece a tu cuenta.");
    }

    if (!cupon.getActivo()
            || cupon.getUsado()
            || cupon.getFechaExpiracion() == null
            || !cupon.getFechaExpiracion().isAfter(LocalDateTime.now())) {
        throw new RuntimeException("Cupón expirado, inactivo o usado.");
    }

    return cupon;
}

private BigDecimal calcularDescuentoCupon(
        Carrito carrito, Cupon cupon) {

    BigDecimal subtotalElegible = BigDecimal.ZERO;

    for (DetalleCarrito detalle : carrito.getDetalles()) {
        Producto producto = detalle.getProducto();

        BigDecimal precioVenta = producto.getPrecioVenta();
        BigDecimal precioConDescuento = producto.getPrecioConDescuento();

        boolean tieneDescuento = precioConDescuento != null
                && precioConDescuento.compareTo(precioVenta) < 0;

        // El cupón no se aplica a productos ya rebajados.
        if (!tieneDescuento) {
            BigDecimal precio = obtenerPrecioConDescuento(producto);

            BigDecimal importe = precio.multiply(
                    BigDecimal.valueOf(detalle.getCantidad()));

            subtotalElegible = subtotalElegible.add(importe);
        }
    }

    if (subtotalElegible.compareTo(BigDecimal.ZERO) <= 0) {
        throw new RuntimeException(
                "El cupón solo se puede aplicar a productos sin descuento.");
    }

    return subtotalElegible
            .multiply(cupon.getValorDescuento())
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
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
