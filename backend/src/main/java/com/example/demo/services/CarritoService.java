package com.example.demo.services;

import com.example.demo.dto.ItemExpiradoDTO;
import com.example.demo.exceptions.CarritoException;
import com.example.demo.models.Carrito;
import com.example.demo.models.DetalleCarrito;
import com.example.demo.models.EstadoDetalleCarrito;
import com.example.demo.models.PresentacionProducto;
import com.example.demo.models.Producto;
import com.example.demo.models.User;
import com.example.demo.repositories.CarritoRepository;
import com.example.demo.repositories.DetalleCarritoRepository;
import com.example.demo.repositories.ProductoRepository;
import com.example.demo.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicio que gestiona el carrito de compras de los usuarios.
 *
 * Cada usuario tiene exactamente UN carrito (relación OneToOne).
 * Cada línea de detalle reserva stock durante 2 horas. Si el usuario no
 * compra en ese plazo, la reserva se libera y la línea se elimina en el
 * siguiente GET del carrito (devolviendo la lista de expirados para avisar).
 */
@Service
public class CarritoService {

    /** Límite de unidades de un mismo producto por carrito. */
    private static final int LIMITE_POR_PRODUCTO = 10;

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private DetalleCarritoRepository detalleCarritoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private StockService stockService;

    /** Presentaciones (blíster, caja...) de los productos que comparten stock. */
    @Autowired
    private PresentacionService presentacionService;

    @Value("${app.carrito.reserva.horas:2}")
    private long horasReserva;

    /**
     * Obtiene el carrito existente del usuario, o crea uno nuevo si no tiene.
     */
    @Transactional
    public Carrito obtenerOCrearCarrito(Integer idUsuario) {
        return carritoRepository.findByUsuarioIdForUpdate(idUsuario).orElseGet(() -> {
            User usuario = userRepository.findById(idUsuario)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            Carrito nuevoCarrito = new Carrito();
            nuevoCarrito.setUsuario(usuario);
            nuevoCarrito.setDetalles(new ArrayList<>());
            return carritoRepository.save(nuevoCarrito);
        });
    }

    /**
     * Devuelve el carrito limpio: elimina las reservas vencidas y adjunta la
     * lista de productos expirados para que el frontend notifique al usuario.
     */
    @Transactional
    public Carrito obtenerCarritoConLimpieza(Integer idUsuario) {
        Carrito carrito = obtenerOCrearCarrito(idUsuario);
        carrito.setExpirados(limpiarExpiradas(carrito));
        return carrito;
    }

    /**
     * Agrega un producto al carrito (sin elegir presentación: usa la predeterminada si el producto la tiene).
     */
    @Transactional
    public Carrito agregarProductoAlCarrito(Integer idUsuario, Integer idProducto, Integer cantidad) {
        return agregarProductoAlCarrito(idUsuario, idProducto, cantidad, null);
    }

    /**
     * Agrega un producto al carrito reservando stock durante 2 horas, opcionalmente en una presentación
     * concreta (blíster, caja...).
     *
     * El stock y las reservas se cuentan en UNIDADES BASE (tabletas): 2 cajas de 100 reservan 200. Una caja y un
     * blíster del mismo medicamento comparten el mismo stock disponible. Un producto sin presentaciones se
     * comporta exactamente como antes (1 presentación = 1 unidad).
     */
    @Transactional
    public Carrito agregarProductoAlCarrito(Integer idUsuario, Integer idProducto, Integer cantidad,
                                            Integer idPresentacion) {
        Carrito carrito = obtenerOCrearCarrito(idUsuario);
        List<ItemExpiradoDTO> expirados = limpiarExpiradas(carrito);
        carrito.setExpirados(expirados);

        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new CarritoException("Producto no encontrado.", 0));

        if (cantidad == null || cantidad <= 0) {
            throw new CarritoException("La cantidad debe ser un número entero mayor a cero.", 0);
        }

        if (producto.isAgotadoOnline()) {
            throw new CarritoException("Este producto está agotado por ahora. Estamos trabajando para reabastecerlo.", 0);
        }

        // Presentación elegida (la predeterminada si no se eligió; null si el producto no tiene presentaciones).
        PresentacionProducto presentacion = presentacionService.resolver(producto, idPresentacion);
        int unidadesPorPresentacion = presentacionService.unidadesDe(presentacion);

        Optional<DetalleCarrito> detalleExistente = lineaActivaDe(carrito, idProducto, presentacion);
        int cantidadActual = detalleExistente
                .map(d -> d.getCantidad() != null ? d.getCantidad() : 0)
                .orElse(0);

        // Unidades base que ya reservan las OTRAS líneas de este producto en el carrito (otra presentación).
        int unidadesEnOtrasLineas = carrito.getDetalles().stream()
                .filter(d -> d.getEstado() == EstadoDetalleCarrito.ACTIVA)
                .filter(d -> d.getProducto() != null && idProducto.equals(d.getProducto().getIdProducto()))
                .filter(d -> detalleExistente.isEmpty() || d != detalleExistente.get())
                .mapToInt(presentacionService::unidadesDeLinea)
                .sum();

        // El stock disponible ya descuenta TODAS las reservas, incluida la de este usuario. Las reglas de
        // presentaciones parten del stock total y restan las líneas propias, así que se las devolvemos aquí
        // para no descontarlas dos veces.
        int disponible = producto.getStockDisponible();
        int stockParaReglas = disponible + unidadesEnOtrasLineas + cantidadActual * unidadesPorPresentacion;

        ReglasPresentacion.LimiteAgregar limite = ReglasPresentacion.limiteParaAgregar(
                stockParaReglas, unidadesPorPresentacion, cantidadActual, unidadesEnOtrasLineas);
        int limitePorProducto = limite.limitePorProducto();
        int maxAdicional = limite.maxAdicional();

        if (maxAdicional <= 0) {
            if (limitePorProducto <= 0) {
                throw new CarritoException(presentacion == null
                        ? "Ya tienes el máximo permitido de 10 unidades de este producto en el carrito."
                        : "Ya tienes el máximo permitido de 10 de esta presentación en el carrito.", 0);
            }
            throw new CarritoException(presentacion == null
                    ? "Este producto no tiene stock disponible para agregar más unidades."
                    : "No hay stock suficiente de esta presentación para agregar más.", 0);
        }

        if (cantidad > maxAdicional) {
            String motivo;
            if (limite.topeEsElLimite()) {
                motivo = presentacion == null
                        ? "el límite es de 10 unidades por producto"
                        : "el límite es de 10 por presentación";
            } else {
                motivo = presentacion == null
                        ? "solo hay " + disponible + " unidad(es) disponibles en stock"
                        : "el stock disponible solo alcanza para " + maxAdicional + " más de esta presentación";
            }
            throw new CarritoException(
                    "No se pueden agregar " + cantidad + " unidades porque " + motivo
                            + ". Puedes agregar " + maxAdicional + " unidad(es) más como máximo.",
                    maxAdicional);
        }

        // Reserva atómica en unidades base: bloquea el producto y valida el disponible otra vez.
        stockService.reservar(idProducto, cantidad * unidadesPorPresentacion);

        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);

        if (detalleExistente.isPresent()) {
            // La fecha de expiración NO se extiende al aumentar la cantidad.
            DetalleCarrito detalle = detalleExistente.get();
            detalle.setCantidad(cantidadActual + cantidad);
            detalleCarritoRepository.save(detalle);
        } else {
            DetalleCarrito nuevoDetalle = new DetalleCarrito();
            nuevoDetalle.setCarrito(carrito);
            nuevoDetalle.setProducto(producto);
            nuevoDetalle.setPresentacion(presentacion);
            nuevoDetalle.setCantidad(cantidad);
            nuevoDetalle.setEstado(EstadoDetalleCarrito.ACTIVA);
            nuevoDetalle.setFechaCreacion(ahora);
            nuevoDetalle.setFechaExpiracion(ahora.plusHours(horasReserva));
            carrito.getDetalles().add(nuevoDetalle);
            detalleCarritoRepository.save(nuevoDetalle);
        }

        return carritoRepository.save(carrito);
    }

    /**
     * Disminuye en 1 la cantidad de un producto (primera línea del producto) y libera esa unidad.
     */
    @Transactional
    public Carrito disminuirProductoDelCarrito(Integer idUsuario, Integer idProducto) {
        return disminuirProductoDelCarrito(idUsuario, idProducto, null);
    }

    /**
     * Disminuye en 1 la cantidad de una línea y libera lo que esa unidad tenía reservado. Con "idPresentacion" se
     * elige la línea de esa presentación; sin ella, la primera línea activa del producto.
     */
    @Transactional
    public Carrito disminuirProductoDelCarrito(Integer idUsuario, Integer idProducto, Integer idPresentacion) {
        Carrito carrito = obtenerOCrearCarrito(idUsuario);
        List<ItemExpiradoDTO> expirados = limpiarExpiradas(carrito);
        carrito.setExpirados(expirados);

        Optional<DetalleCarrito> detalleExistente = buscarLineaActiva(carrito, idProducto, idPresentacion);

        if (detalleExistente.isPresent()) {
            DetalleCarrito detalle = detalleExistente.get();
            int unidadesPorUnaPresentacion = presentacionService.unidadesDe(presentacionService.presentacionDeLinea(detalle));
            if (detalle.getCantidad() != null && detalle.getCantidad() > 1) {
                detalle.setCantidad(detalle.getCantidad() - 1);
                detalleCarritoRepository.save(detalle);
            } else {
                carrito.getDetalles().remove(detalle);
                detalleCarritoRepository.delete(detalle);
            }
            stockService.liberar(idProducto, unidadesPorUnaPresentacion);
        }

        return carritoRepository.save(carrito);
    }

    /**
     * Elimina completamente un producto del carrito (primera línea del producto) y libera su reserva.
     */
    @Transactional
    public Carrito eliminarProductoDelCarrito(Integer idUsuario, Integer idProducto) {
        return eliminarProductoDelCarrito(idUsuario, idProducto, null);
    }

    /**
     * Elimina una línea completa y libera toda su reserva. Con "idPresentacion" se elige la línea de esa
     * presentación; sin ella, la primera línea activa del producto.
     */
    @Transactional
    public Carrito eliminarProductoDelCarrito(Integer idUsuario, Integer idProducto, Integer idPresentacion) {
        Carrito carrito = obtenerOCrearCarrito(idUsuario);
        List<ItemExpiradoDTO> expirados = limpiarExpiradas(carrito);
        carrito.setExpirados(expirados);

        Optional<DetalleCarrito> detalleExistente = buscarLineaActiva(carrito, idProducto, idPresentacion);

        if (detalleExistente.isPresent()) {
            DetalleCarrito detalle = detalleExistente.get();
            int unidades = presentacionService.unidadesDeLinea(detalle);
            carrito.getDetalles().remove(detalle);
            detalleCarritoRepository.delete(detalle);
            stockService.liberar(idProducto, unidades);
        }

        return carritoRepository.save(carrito);
    }

    /**
     * Vacía completamente el carrito liberando todas las reservas activas.
     */
    @Transactional
    public Carrito vaciarCarrito(Integer idUsuario) {
        Carrito carrito = obtenerOCrearCarrito(idUsuario);
        List<ItemExpiradoDTO> expirados = limpiarExpiradas(carrito);
        carrito.setExpirados(expirados);

        for (DetalleCarrito detalle : new ArrayList<>(carrito.getDetalles())) {
            if (detalle.getEstado() == EstadoDetalleCarrito.ACTIVA) {
                stockService.liberar(detalle.getProducto().getIdProducto(),
                        presentacionService.unidadesDeLinea(detalle));
            }
        }

        detalleCarritoRepository.deleteAll(carrito.getDetalles());
        carrito.getDetalles().clear();
        return carritoRepository.save(carrito);
    }

    /**
     * Vacía el carrito SIN liberar reservas.
     * Se usa al confirmar el pago, porque StockService.confirmarVenta ya
     * descontó la reserva y el stock físico.
     */
    @Transactional
    public void vaciarCarritoSinLiberar(Integer idUsuario) {
        Carrito carrito = obtenerOCrearCarrito(idUsuario);
        detalleCarritoRepository.deleteAll(carrito.getDetalles());
        carrito.getDetalles().clear();
        carritoRepository.save(carrito);
    }

    /**
     * Elimina las líneas vencidas del carrito.
     * - Si la línea seguía ACTIVA (el scheduler no llegó), libera su stock.
     * - Si ya estaba EXPIRADA, el stock ya fue liberado y solo se elimina.
     *
     * @return los productos expirados para notificar al usuario.
     */
    private List<ItemExpiradoDTO> limpiarExpiradas(Carrito carrito) {
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        List<ItemExpiradoDTO> expirados = new ArrayList<>();
        List<DetalleCarrito> aEliminar = new ArrayList<>();

        for (DetalleCarrito detalle : new ArrayList<>(carrito.getDetalles())) {
            boolean vencida = detalle.getEstado() == EstadoDetalleCarrito.EXPIRADA
                    || (detalle.getEstado() == EstadoDetalleCarrito.ACTIVA
                    && detalle.getFechaExpiracion() != null
                    && !detalle.getFechaExpiracion().isAfter(ahora));

            if (!vencida) {
                continue;
            }

            if (detalle.getEstado() == EstadoDetalleCarrito.ACTIVA) {
                // Marca la línea como EXPIRADA de forma atómica: solo la ejecución
                // que realmente la marca libera el stock (evita liberar dos veces).
                int actualizadas = detalleCarritoRepository.marcarExpiradaSiActiva(
                        detalle.getIdDetalleCarrito(), ahora,
                        EstadoDetalleCarrito.EXPIRADA, EstadoDetalleCarrito.ACTIVA);
                if (actualizadas > 0) {
                    stockService.liberar(detalle.getProducto().getIdProducto(),
                            presentacionService.unidadesDeLinea(detalle));
                }
            }

            expirados.add(new ItemExpiradoDTO(
                    detalle.getProducto().getIdProducto(),
                    detalle.getProducto().getNombre(),
                    detalle.getCantidad(),
                    detalle.getFechaExpiracion(),
                    "La reserva de 2 horas venció y el producto volvió al stock."));

            carrito.getDetalles().remove(detalle);
            aEliminar.add(detalle);
        }

        if (!aEliminar.isEmpty()) {
            detalleCarritoRepository.deleteAll(aEliminar);
            carritoRepository.save(carrito);
        }

        return expirados;
    }

    /**
     * Línea ACTIVA del carrito que corresponde a un producto y a la presentación efectiva indicada. Una línea
     * anterior a las presentaciones (sin presentación guardada) equivale a la presentación predeterminada.
     */
    private Optional<DetalleCarrito> lineaActivaDe(Carrito carrito, Integer idProducto, PresentacionProducto presentacion) {
        Integer idBuscado = presentacion == null ? null : presentacion.getIdPresentacion();
        return carrito.getDetalles().stream()
                .filter(d -> d.getEstado() == EstadoDetalleCarrito.ACTIVA)
                .filter(d -> d.getProducto() != null
                        && d.getProducto().getIdProducto() != null
                        && d.getProducto().getIdProducto().equals(idProducto))
                .filter(d -> {
                    PresentacionProducto deLinea = presentacionService.presentacionDeLinea(d);
                    return Objects.equals(deLinea == null ? null : deLinea.getIdPresentacion(), idBuscado);
                })
                .findFirst();
    }

    /** Sin presentación indicada: la primera línea activa del producto; con ella, la línea de esa presentación. */
    private Optional<DetalleCarrito> buscarLineaActiva(Carrito carrito, Integer idProducto, Integer idPresentacion) {
        return carrito.getDetalles().stream()
                .filter(d -> d.getEstado() == EstadoDetalleCarrito.ACTIVA)
                .filter(d -> d.getProducto() != null
                        && d.getProducto().getIdProducto() != null
                        && d.getProducto().getIdProducto().equals(idProducto))
                .filter(d -> {
                    if (idPresentacion == null) {
                        return true;
                    }
                    PresentacionProducto deLinea = presentacionService.presentacionDeLinea(d);
                    return deLinea != null && idPresentacion.equals(deLinea.getIdPresentacion());
                })
                .findFirst();
    }
}
