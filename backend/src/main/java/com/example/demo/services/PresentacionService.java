package com.example.demo.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.dto.PresentacionDTO;
import com.example.demo.exceptions.CarritoException;
import com.example.demo.models.DetalleCarrito;
import com.example.demo.models.PresentacionProducto;
import com.example.demo.models.Producto;
import com.example.demo.repositories.PresentacionProductoRepository;

/**
 * Presentaciones (blíster, caja...) de los productos que comparten un mismo stock.
 *
 * Regla de oro: un producto SIN presentaciones se comporta exactamente como antes. Un producto CON presentaciones
 * usa la presentación predeterminada (la caja) cuando nadie elige otra, así los clientes y sistemas que todavía no
 * conocen las presentaciones siguen comprando correctamente.
 */
@Service
public class PresentacionService {

    private final PresentacionProductoRepository repo;

    public PresentacionService(PresentacionProductoRepository repo) {
        this.repo = repo;
    }

    /** Presentaciones activas del producto (vacía si se vende sin presentaciones). */
    public List<PresentacionProducto> activasDe(Integer idProducto) {
        return repo.findByProductoIdProductoAndActivoTrueOrderByOrdenAscIdPresentacionAsc(idProducto);
    }

    /**
     * Presentación con la que se quiere comprar un producto.
     *
     * @return la elegida; la predeterminada si no se eligió ninguna; null si el producto no tiene presentaciones.
     * @throws CarritoException si se pidió una presentación que no existe o no pertenece al producto.
     */
    public PresentacionProducto resolver(Producto producto, Integer idPresentacion) {
        List<PresentacionProducto> activas = activasDe(producto.getIdProducto());
        if (activas.isEmpty()) {
            if (idPresentacion != null) {
                throw new CarritoException("Este producto no se vende por presentaciones.", 0);
            }
            return null;
        }
        if (idPresentacion == null) {
            return predeterminada(activas);
        }
        return activas.stream()
                .filter(p -> idPresentacion.equals(p.getIdPresentacion()))
                .findFirst()
                .orElseThrow(() -> new CarritoException("Esa presentación no está disponible para este producto.", 0));
    }

    private static PresentacionProducto predeterminada(List<PresentacionProducto> activas) {
        return activas.stream().filter(PresentacionProducto::isEsPredeterminada).findFirst().orElse(activas.get(0));
    }

    /**
     * Presentación de una línea del carrito: la guardada en la línea o, si la línea es anterior a las
     * presentaciones, la predeterminada del producto (null si el producto no tiene presentaciones).
     */
    public PresentacionProducto presentacionDeLinea(DetalleCarrito linea) {
        if (linea.getPresentacion() != null) {
            return linea.getPresentacion();
        }
        List<PresentacionProducto> activas = activasDe(linea.getProducto().getIdProducto());
        return activas.isEmpty() ? null : predeterminada(activas);
    }

    /** Unidades base que descuenta una unidad de la presentación (1 si el producto no tiene presentaciones). */
    public int unidadesDe(PresentacionProducto presentacion) {
        return presentacion == null || presentacion.getUnidades() == null ? 1 : Math.max(1, presentacion.getUnidades());
    }

    /** Unidades base que consume toda una línea del carrito. */
    public int unidadesDeLinea(DetalleCarrito linea) {
        int cantidad = linea.getCantidad() == null ? 0 : linea.getCantidad();
        return ReglasPresentacion.unidadesDeLinea(cantidad, unidadesDe(presentacionDeLinea(linea)));
    }

    /** Precio que paga el cliente por una unidad (con la oferta del producto). Igual que antes si no hay presentación. */
    public BigDecimal precioUnitario(Producto producto, PresentacionProducto presentacion) {
        return ReglasPresentacion.precioUnitario(
                presentacion == null ? null : presentacion.getPrecioVenta(),
                producto.getPrecioVenta(),
                producto.getPrecioConDescuento());
    }

    /** Precio SIN oferta de una unidad de la presentación. */
    public BigDecimal precioRegular(Producto producto, PresentacionProducto presentacion) {
        return ReglasPresentacion.precioRegular(presentacion == null ? null : presentacion.getPrecioVenta(),
                producto.getPrecioVenta());
    }

    /** Presentaciones de un producto listas para el selector del cliente. */
    public List<PresentacionDTO> paraMostrar(Producto producto) {
        // Stock realmente disponible: físico menos lo reservado en carritos (en unidad base).
        int stock = Math.max(0, producto.getStockDisponible());
        boolean vencido = producto.getFechaCaducidad() != null && producto.getFechaCaducidad().isBefore(LocalDate.now());
        return activasDe(producto.getIdProducto()).stream()
                .map(p -> new PresentacionDTO(
                        p.getIdPresentacion(),
                        p.getEtiqueta(),
                        p.getUnidades(),
                        precioUnitario(producto, p),
                        precioRegular(producto, p),
                        p.isEsPredeterminada(),
                        !vencido && ReglasPresentacion.disponible(stock, unidadesDe(p)),
                        vencido ? 0 : ReglasPresentacion.maxPorPedido(stock, unidadesDe(p))))
                .toList();
    }
}
