package com.example.demo.services;

import com.example.demo.exceptions.CarritoException;
import com.example.demo.models.InventarioLote;
import com.example.demo.models.Producto;
import com.example.demo.repositories.InventarioLoteRepository;
import com.example.demo.repositories.ProductoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Único punto de entrada para mover stock.
 *
 * Reglas:
 * - {@code stock} es el stock físico (lotes o stock manual).
 * - {@code stockEnCarrito} son las unidades reservadas por carritos activos.
 * - {@code stockDisponible = stock - stockEnCarrito}.
 *
 * Reservar/liberar bloquea la fila del producto para que dos usuarios no
 * puedan apartar la misma unidad al mismo tiempo.
 */
@Service
public class StockService {

    private final ProductoRepository productoRepository;
    private final InventarioLoteRepository loteRepository;
    private final AlgoliaService algoliaService;

    @PersistenceContext
    private EntityManager entityManager;

    public StockService(ProductoRepository productoRepository,
                        InventarioLoteRepository loteRepository,
                        AlgoliaService algoliaService) {
        this.productoRepository = productoRepository;
        this.loteRepository = loteRepository;
        this.algoliaService = algoliaService;
    }

    /**
     * Aparta unidades para un carrito. Debe ejecutarse dentro de una transacción.
     *
     * @return el producto actualizado.
     * @throws CarritoException si no hay stock disponible suficiente.
     */
    @Transactional
    public Producto reservar(Integer idProducto, int cantidad) {
        if (cantidad <= 0) {
            throw new CarritoException("La cantidad debe ser un número entero mayor a cero.", 0);
        }

        Producto producto = productoRepository.findByIdForUpdate(idProducto)
                .orElseThrow(() -> new CarritoException("Producto no encontrado.", 0));
        // El producto pudo quedar en el contexto de persistencia con datos viejos:
        // refrescarlo asegura leer el stock real ya bloqueado.
        entityManager.refresh(producto);

        int disponible = producto.getStockDisponible();
        if (disponible < cantidad) {
            throw new CarritoException(
                    "Solo hay " + disponible + " unidad(es) disponibles en stock.", disponible);
        }

        producto.setStockEnCarrito((producto.getStockEnCarrito() != null ? producto.getStockEnCarrito() : 0) + cantidad);
        Producto guardado = productoRepository.save(producto);
        reindexarDespues(idProducto);
        return guardado;
    }

    /**
     * Devuelve unidades reservadas al stock disponible (disminuir, eliminar, vaciar o expirar).
     */
    @Transactional
    public void liberar(Integer idProducto, int cantidad) {
        if (cantidad <= 0) {
            return;
        }
        Producto producto = productoRepository.findByIdForUpdate(idProducto).orElse(null);
        if (producto == null) {
            return;
        }
        entityManager.refresh(producto);
        int enCarrito = producto.getStockEnCarrito() != null ? producto.getStockEnCarrito() : 0;
        producto.setStockEnCarrito(Math.max(0, enCarrito - cantidad));
        productoRepository.save(producto);
        reindexarDespues(idProducto);
    }

    /**
     * Confirma una venta que YA tenía reserva:
     * quita la reserva y descuenta el stock físico (FEFO).
     */
    @Transactional
    public void confirmarVenta(Producto producto, int cantidad) {
        Producto actual = productoRepository.findByIdForUpdate(producto.getIdProducto())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + producto.getIdProducto()));
        entityManager.refresh(actual);

        int enCarrito = actual.getStockEnCarrito() != null ? actual.getStockEnCarrito() : 0;
        if (enCarrito < cantidad) {
            throw new RuntimeException("La reserva de \"" + actual.getNombre()
                    + "\" ya no está activa. Vuelve a agregarlo al carrito.");
        }

        boolean tieneLotes = descontarFisicoFefo(actual, cantidad);

        // Si se descontaron lotes, el trigger de la BD ya actualizó productos.stock.
        // Hay que refrescar la entidad ANTES de guardarla; de lo contrario el UPDATE
        // de Hibernate escribiría el stock viejo y pisaría el valor del trigger.
        if (tieneLotes) {
            entityManager.flush();
            entityManager.refresh(actual);
        }

        actual.setStockEnCarrito(enCarrito - cantidad);
        productoRepository.save(actual);
        reindexarDespues(actual.getIdProducto());
    }

    /**
     * Venta presencial (POS) sin reserva previa: valida el disponible y
     * descuenta directamente el stock físico.
     */
    @Transactional
    public void venderSinReserva(Integer idProducto, int cantidad) {
        Producto producto = productoRepository.findByIdForUpdate(idProducto)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + idProducto));
        entityManager.refresh(producto);

        if (producto.getStockDisponible() < cantidad) {
            throw new RuntimeException("Stock disponible insuficiente para " + producto.getNombre()
                    + ". Disponible: " + producto.getStockDisponible() + " unidad(es).");
        }

        boolean tieneLotes = descontarFisicoFefo(producto, cantidad);

        if (tieneLotes) {
            // El trigger ya recalculó productos.stock; no guardamos la entidad para
            // no sobrescribir ese valor con el stock anterior.
            entityManager.flush();
            entityManager.refresh(producto);
        } else {
            productoRepository.save(producto);
        }
        reindexarDespues(idProducto);
    }

    /**
     * Ajuste manual desde el panel de administración.
     * No permite dejar el stock físico por debajo de lo reservado.
     * Si el producto tiene lotes, el ajuste se hace sobre lotes para que el
     * trigger de Supabase no sobrescriba el cambio.
     */
    @Transactional
    public Producto ajustarStock(Integer idProducto, int nuevoStock) {
        if (nuevoStock < 0) {
            throw new RuntimeException("El stock debe ser un número entero no negativo.");
        }

        Producto producto = productoRepository.findByIdForUpdate(idProducto)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + idProducto));
        entityManager.refresh(producto);

        int reservado = producto.getStockEnCarrito() != null ? producto.getStockEnCarrito() : 0;
        if (nuevoStock < reservado) {
            throw new RuntimeException("No puedes dejar el stock en " + nuevoStock
                    + ": hay " + reservado + " unidad(es) reservadas en carritos.");
        }

        List<InventarioLote> lotes = loteRepository
                .findByProductoIdProductoOrderByFechaVencimientoAsc(idProducto);

        if (lotes.isEmpty()) {
            producto.setStock(nuevoStock);
            productoRepository.save(producto);
        } else {
            int actual = lotes.stream()
                    .mapToInt(l -> l.getCantidadActual() != null ? l.getCantidadActual() : 0)
                    .sum();
            int diferencia = nuevoStock - actual;

            if (diferencia > 0) {
                InventarioLote ajuste = new InventarioLote();
                ajuste.setCodigoLote("AJUSTE-" + UUID.randomUUID());
                ajuste.setProducto(producto);
                ajuste.setCantidadActual(diferencia);
                ajuste.setFechaIngreso(LocalDate.now());
                LocalDate vencimiento = lotes.stream()
                        .filter(l -> l.getCantidadActual() != null && l.getCantidadActual() > 0)
                        .map(InventarioLote::getFechaVencimiento)
                        .filter(f -> f != null && !f.isBefore(LocalDate.now()))
                        .max(Comparator.naturalOrder())
                        .orElse(LocalDate.now().plusYears(1));
                ajuste.setFechaVencimiento(vencimiento);
                loteRepository.save(ajuste);
                entityManager.flush();
                entityManager.refresh(producto);
            } else if (diferencia < 0) {
                int porReducir = -diferencia;
                List<InventarioLote> modificados = new ArrayList<>();
                for (InventarioLote lote : lotes) {
                    if (porReducir <= 0) {
                        break;
                    }
                    int cantidad = lote.getCantidadActual() != null ? lote.getCantidadActual() : 0;
                    if (cantidad <= 0) {
                        continue;
                    }
                    int usar = Math.min(cantidad, porReducir);
                    lote.setCantidadActual(cantidad - usar);
                    porReducir -= usar;
                    modificados.add(lote);
                }
                if (porReducir > 0) {
                    throw new RuntimeException("No se pudo ajustar el stock de los lotes del producto.");
                }
                loteRepository.saveAll(modificados);
                entityManager.flush();
                entityManager.refresh(producto);
            }
            // El trigger sincronizar_stock_lotes actualiza productos.stock.
        }

        reindexarDespues(idProducto);
        return producto;
    }

    /**
     * Descuenta el stock físico respetando FEFO, pero sin consumir lotes vencidos.
     * Si el producto no tiene lotes, descuenta el stock manual.
     *
     * @return true si el descuento se hizo sobre lotes (el trigger de la BD
     *         recalcula {@code productos.stock}); false si fue stock manual.
     */
    private boolean descontarFisicoFefo(Producto producto, int cantidad) {
        List<InventarioLote> lotes = loteRepository
                .findByProductoIdProductoOrderByFechaVencimientoAsc(producto.getIdProducto());

        if (lotes.isEmpty()) {
            int stockActual = producto.getStock() != null ? producto.getStock() : 0;
            if (stockActual < cantidad) {
                throw new RuntimeException("Stock insuficiente para el producto: " + producto.getNombre());
            }
            producto.setStock(stockActual - cantidad);
            return false;
        }

        LocalDate hoy = LocalDate.now();
        int restante = cantidad;
        List<InventarioLote> modificados = new ArrayList<>();

        for (InventarioLote lote : lotes) {
            if (restante <= 0) {
                break;
            }
            int disponible = lote.getCantidadActual() != null ? lote.getCantidadActual() : 0;
            if (disponible <= 0) {
                continue;
            }
            // No vender lotes vencidos.
            if (lote.getFechaVencimiento() != null && lote.getFechaVencimiento().isBefore(hoy)) {
                continue;
            }
            int usar = Math.min(disponible, restante);
            lote.setCantidadActual(disponible - usar);
            restante -= usar;
            modificados.add(lote);
        }

        if (restante > 0) {
            throw new RuntimeException("Stock insuficiente en lotes vigentes para el producto: "
                    + producto.getNombre());
        }

        if (!modificados.isEmpty()) {
            loteRepository.saveAll(modificados);
        }
        return true;
    }

    /**
     * Reindexa Algolia después del commit, sin bloquear la transacción de stock.
     */
    private void reindexarDespues(Integer idProducto) {
        AfterCommitRunner.run(() -> productoRepository.findById(idProducto)
                .ifPresent(algoliaService::indexarProducto));
    }
}
