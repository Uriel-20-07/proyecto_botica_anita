package com.example.demo.services;

import com.example.demo.models.Carrito;
import com.example.demo.models.DetalleCarrito;
import com.example.demo.models.Producto;
import com.example.demo.models.User;
import com.example.demo.repositories.CarritoRepository;
import com.example.demo.repositories.DetalleCarritoRepository;
import com.example.demo.repositories.ProductoRepository;
import com.example.demo.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Servicio que gestiona el carrito de compras de los usuarios.
 * 
 * Cada usuario tiene exactamente UN carrito (relación OneToOne).
 * El carrito contiene una lista de líneas de detalle (DetalleCarrito),
 * donde cada línea representa un producto con su cantidad.
 * 
 * Todas las operaciones son transaccionales para garantizar consistencia
 * en la base de datos (si alguna operación falla, se revierte todo).
 */
@Service
public class CarritoService {

    /** Repositorio del carrito (cabecera). */
    @Autowired
    private CarritoRepository carritoRepository;

    /** Repositorio de las líneas de detalle del carrito. */
    @Autowired
    private DetalleCarritoRepository detalleCarritoRepository;

    /** Repositorio de usuarios (para verificar existencia). */
    @Autowired
    private UserRepository userRepository;

    /** Repositorio de productos (para verificar existencia y obtener datos). */
    @Autowired
    private ProductoRepository productoRepository;

    /**
     * Obtiene el carrito existente del usuario, o crea uno nuevo si no tiene.
     * 
     * Es el método "get or create" del patrón lazy initialization.
     * Se llama antes de cualquier operación sobre el carrito para garantizar
     * que siempre haya un carrito disponible.
     *
     * @param idUsuario ID del usuario propietario del carrito.
     * @return el carrito del usuario (existente o recién creado).
     * @throws RuntimeException si el usuario no existe.
     */
    @Transactional
    public Carrito obtenerOCrearCarrito(Integer idUsuario) {
        return carritoRepository.findByUsuario_Id(idUsuario).orElseGet(() -> {
            // Si el usuario no tiene carrito, crear uno vacío
            User usuario = userRepository.findById(idUsuario).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            Carrito nuevoCarrito = new Carrito();
            nuevoCarrito.setUsuario(usuario);
            nuevoCarrito.setDetalles(new ArrayList<>());
            return carritoRepository.save(nuevoCarrito);
        });
    }

    /**
     * Agrega un producto al carrito del usuario.
     * 
     * Comportamiento:
     * - Si el producto YA está en el carrito: incrementa la cantidad.
     * - Si el producto NO está en el carrito: crea una nueva línea de detalle.
     *
     * @param idUsuario  ID del usuario dueño del carrito.
     * @param idProducto ID del producto a agregar.
     * @param cantidad   número de unidades a agregar.
     * @return el carrito actualizado con el producto agregado/incrementado.
     * @throws RuntimeException si el producto no existe.
     */
    @Transactional
    public Carrito agregarProductoAlCarrito(Integer idUsuario, Integer idProducto, Integer cantidad) {
        Carrito carrito = obtenerOCrearCarrito(idUsuario);
        Producto producto = productoRepository.findById(idProducto).orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        // Buscar si el producto ya está en el carrito
        Optional<DetalleCarrito> detalleExistente = carrito.getDetalles().stream()
                .filter(d -> d.getProducto().getIdProducto().equals(idProducto))
                .findFirst();

        if (detalleExistente.isPresent()) {
            // El producto ya existe: solo incrementar la cantidad
            DetalleCarrito detalle = detalleExistente.get();
            detalle.setCantidad(detalle.getCantidad() + cantidad);
            detalleCarritoRepository.save(detalle);
        } else {
            // El producto es nuevo en el carrito: crear línea de detalle
            DetalleCarrito nuevoDetalle = new DetalleCarrito();
            nuevoDetalle.setCarrito(carrito);
            nuevoDetalle.setProducto(producto);
            nuevoDetalle.setCantidad(cantidad);
            carrito.getDetalles().add(nuevoDetalle);
            detalleCarritoRepository.save(nuevoDetalle);
        }

        return carritoRepository.save(carrito);
    }

    /**
     * Disminuye en 1 la cantidad de un producto en el carrito.
     * 
     * Comportamiento:
     * - Si cantidad > 1: disminuye en 1 la cantidad.
     * - Si cantidad = 1: elimina la línea del carrito completamente.
     *
     * @param idUsuario  ID del usuario dueño del carrito.
     * @param idProducto ID del producto a disminuir.
     * @return el carrito actualizado.
     */
    @Transactional
    public Carrito disminuirProductoDelCarrito(Integer idUsuario, Integer idProducto) {
        Carrito carrito = obtenerOCrearCarrito(idUsuario);

        Optional<DetalleCarrito> detalleExistente = carrito.getDetalles().stream()
                .filter(d -> d.getProducto().getIdProducto().equals(idProducto))
                .findFirst();

        if (detalleExistente.isPresent()) {
            DetalleCarrito detalle = detalleExistente.get();
            if (detalle.getCantidad() > 1) {
                // Aún quedan unidades: decrementar
                detalle.setCantidad(detalle.getCantidad() - 1);
                detalleCarritoRepository.save(detalle);
            } else {
                // Era la última unidad: eliminar la línea del carrito
                carrito.getDetalles().remove(detalle);
                detalleCarritoRepository.delete(detalle);
            }
        }

        return carritoRepository.save(carrito);
    }

    /**
     * Elimina completamente un producto del carrito (sin importar la cantidad).
     *
     * @param idUsuario  ID del usuario dueño del carrito.
     * @param idProducto ID del producto a eliminar.
     * @return el carrito actualizado sin el producto eliminado.
     */
    @Transactional
    public Carrito eliminarProductoDelCarrito(Integer idUsuario, Integer idProducto) {
        Carrito carrito = obtenerOCrearCarrito(idUsuario);

        Optional<DetalleCarrito> detalleExistente = carrito.getDetalles().stream()
                .filter(d -> d.getProducto().getIdProducto().equals(idProducto))
                .findFirst();

        if (detalleExistente.isPresent()) {
            DetalleCarrito detalle = detalleExistente.get();
            carrito.getDetalles().remove(detalle);     // Quita de la lista en memoria
            detalleCarritoRepository.delete(detalle);  // Elimina de la base de datos
        }

        return carritoRepository.save(carrito);
    }

    /**
     * Vacía completamente el carrito del usuario (elimina todas las líneas de detalle).
     * Se usa después de completar un pago exitoso.
     *
     * @param idUsuario ID del usuario dueño del carrito.
     * @return el carrito vacío (sin productos).
     */
    @Transactional
    public Carrito vaciarCarrito(Integer idUsuario) {
        Carrito carrito = obtenerOCrearCarrito(idUsuario);
        detalleCarritoRepository.deleteAll(carrito.getDetalles()); // Elimina todas las líneas de la BD
        carrito.getDetalles().clear();                              // Limpia la lista en memoria
        return carritoRepository.save(carrito);
    }
}
