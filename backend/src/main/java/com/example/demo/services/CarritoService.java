package com.example.demo.services;

import com.example.demo.dto.ItemExpiradoDTO;
import com.example.demo.exceptions.CarritoException;
import com.example.demo.models.Carrito;
import com.example.demo.models.DetalleCarrito;
import com.example.demo.models.EstadoDetalleCarrito;
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
import java.util.Optional;

/**
 * Servicio que gestiona el carrito de compras de los usuarios.
 *
 * Cada usuario tiene exactamente UN carrito (relación OneToOne).
 * Cada línea de detalle reserva stock durante 24 horas. Si el usuario no
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

    @Value("${app.carrito.reserva.horas:24}")
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
     * Agrega un producto al carrito reservando stock durante 24 horas.
     */
    @Transactional
    public Carrito agregarProductoAlCarrito(Integer idUsuario, Integer idProducto, Integer cantidad) {
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

        Optional<DetalleCarrito> detalleExistente = buscarDetalleActivo(carrito, idProducto);
        int cantidadActual = detalleExistente
                .map(d -> d.getCantidad() != null ? d.getCantidad() : 0)
                .orElse(0);

        // El stock disponible ya descuenta TODAS las reservas, incluida la de este usuario.
        int disponible = producto.getStockDisponible();
        int limitePorProducto = LIMITE_POR_PRODUCTO - cantidadActual;
        int maxAdicional = Math.max(0, Math.min(limitePorProducto, disponible));

        if (maxAdicional <= 0) {
            if (limitePorProducto <= 0) {
                throw new CarritoException(
                        "Ya tienes el máximo permitido de 10 unidades de este producto en el carrito.", 0);
            }
            throw new CarritoException("Este producto no tiene stock disponible para agregar más unidades.", 0);
        }

        if (cantidad > maxAdicional) {
            boolean topeEsElLimite = limitePorProducto <= disponible;
            String motivo = topeEsElLimite
                    ? "el límite es de 10 unidades por producto"
                    : "solo hay " + disponible + " unidad(es) disponibles en stock";
            throw new CarritoException(
                    "No se pueden agregar " + cantidad + " unidades porque " + motivo
                            + ". Puedes agregar " + maxAdicional + " unidad(es) más como máximo.",
                    maxAdicional);
        }

        // Reserva atómica: bloquea el producto y valida el disponible otra vez.
        stockService.reservar(idProducto, cantidad);

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
     * Disminuye en 1 la cantidad de un producto y libera esa unidad.
     */
    @Transactional
    public Carrito disminuirProductoDelCarrito(Integer idUsuario, Integer idProducto) {
        Carrito carrito = obtenerOCrearCarrito(idUsuario);
        List<ItemExpiradoDTO> expirados = limpiarExpiradas(carrito);
        carrito.setExpirados(expirados);

        Optional<DetalleCarrito> detalleExistente = buscarDetalleActivo(carrito, idProducto);

        if (detalleExistente.isPresent()) {
            DetalleCarrito detalle = detalleExistente.get();
            if (detalle.getCantidad() != null && detalle.getCantidad() > 1) {
                detalle.setCantidad(detalle.getCantidad() - 1);
                detalleCarritoRepository.save(detalle);
            } else {
                carrito.getDetalles().remove(detalle);
                detalleCarritoRepository.delete(detalle);
            }
            stockService.liberar(idProducto, 1);
        }

        return carritoRepository.save(carrito);
    }

    /**
     * Elimina completamente un producto del carrito y libera toda su reserva.
     */
    @Transactional
    public Carrito eliminarProductoDelCarrito(Integer idUsuario, Integer idProducto) {
        Carrito carrito = obtenerOCrearCarrito(idUsuario);
        List<ItemExpiradoDTO> expirados = limpiarExpiradas(carrito);
        carrito.setExpirados(expirados);

        Optional<DetalleCarrito> detalleExistente = buscarDetalleActivo(carrito, idProducto);

        if (detalleExistente.isPresent()) {
            DetalleCarrito detalle = detalleExistente.get();
            int cantidad = detalle.getCantidad() != null ? detalle.getCantidad() : 0;
            carrito.getDetalles().remove(detalle);
            detalleCarritoRepository.delete(detalle);
            stockService.liberar(idProducto, cantidad);
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
                        detalle.getCantidad() != null ? detalle.getCantidad() : 0);
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
                            detalle.getCantidad() != null ? detalle.getCantidad() : 0);
                }
            }

            expirados.add(new ItemExpiradoDTO(
                    detalle.getProducto().getIdProducto(),
                    detalle.getProducto().getNombre(),
                    detalle.getCantidad(),
                    detalle.getFechaExpiracion(),
                    "La reserva de 24 horas venció y el producto volvió al stock."));

            carrito.getDetalles().remove(detalle);
            aEliminar.add(detalle);
        }

        if (!aEliminar.isEmpty()) {
            detalleCarritoRepository.deleteAll(aEliminar);
            carritoRepository.save(carrito);
        }

        return expirados;
    }

    private Optional<DetalleCarrito> buscarDetalleActivo(Carrito carrito, Integer idProducto) {
        return carrito.getDetalles().stream()
                .filter(d -> d.getEstado() == EstadoDetalleCarrito.ACTIVA)
                .filter(d -> d.getProducto() != null
                        && d.getProducto().getIdProducto() != null
                        && d.getProducto().getIdProducto().equals(idProducto))
                .findFirst();
    }
}
