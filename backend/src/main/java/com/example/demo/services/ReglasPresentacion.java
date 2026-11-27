package com.example.demo.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de negocio de las presentaciones (blíster, caja...) de un producto que comparte un mismo stock.
 *
 * El stock del producto se cuenta en la UNIDAD BASE (por ejemplo, tabletas). Cada presentación indica cuántas
 * unidades base contiene: vender 2 cajas de 100 descuenta 200 tabletas.
 *
 * Son funciones puras (sin Spring ni base de datos) para poder probarlas con facilidad. Un producto sin
 * presentaciones se trata como "1 unidad por venta", y todo se comporta igual que antes de que existieran.
 */
public final class ReglasPresentacion {

    /** Máximo de una misma presentación (o de un producto sin presentaciones) por carrito. */
    public static final int MAX_POR_LINEA = 10;

    private ReglasPresentacion() {
    }

    /**
     * @param maxAdicional    cuántas presentaciones más se pueden agregar a la línea (nunca negativo).
     * @param topeEsElLimite  true si el tope lo pone el límite de 10, false si lo pone el stock.
     * @param limitePorProducto cuánto falta para llegar al límite de 10 (puede ser 0).
     * @param limitePorStock  cuántas presentaciones más alcanzan con el stock (puede ser negativo si ya se pasó).
     */
    public record LimiteAgregar(int maxAdicional, boolean topeEsElLimite, int limitePorProducto, int limitePorStock) {
    }

    /**
     * Cuántas presentaciones más se pueden agregar a una línea del carrito.
     *
     * @param stockUnidades               stock del producto, en unidad base.
     * @param unidadesPresentacion        unidades base que trae esta presentación (1 si el producto no tiene).
     * @param cantidadEnLinea             presentaciones que ya hay en ESTA línea.
     * @param unidadesEnOtrasLineas       unidades base que ya consumen las OTRAS líneas del mismo producto
     *                                    (por ejemplo, el blíster cuando se agrega la caja).
     */
    public static LimiteAgregar limiteParaAgregar(int stockUnidades, int unidadesPresentacion,
                                                  int cantidadEnLinea, int unidadesEnOtrasLineas) {
        int unidades = Math.max(1, unidadesPresentacion);
        int limitePorProducto = MAX_POR_LINEA - cantidadEnLinea;
        int unidadesLibres = stockUnidades - unidadesEnOtrasLineas - cantidadEnLinea * unidades;
        int limitePorStock = Math.floorDiv(unidadesLibres, unidades);
        int maxAdicional = Math.max(0, Math.min(limitePorProducto, limitePorStock));
        return new LimiteAgregar(maxAdicional, limitePorProducto <= limitePorStock, limitePorProducto, limitePorStock);
    }

    /** Unidades base que consume una línea: cantidad de presentaciones x unidades de cada una. */
    public static int unidadesDeLinea(int cantidad, Integer unidadesPresentacion) {
        return cantidad * (unidadesPresentacion == null ? 1 : Math.max(1, unidadesPresentacion));
    }

    /** ¿Alcanza el stock para vender al menos una de esta presentación? */
    public static boolean disponible(int stockUnidades, int unidadesPresentacion) {
        return stockUnidades >= Math.max(1, unidadesPresentacion);
    }

    /** Máximo que se puede pedir de una vez de esta presentación: el menor entre el stock y el límite de 10. */
    public static int maxPorPedido(int stockUnidades, int unidadesPresentacion) {
        int porStock = Math.floorDiv(Math.max(0, stockUnidades), Math.max(1, unidadesPresentacion));
        return Math.min(MAX_POR_LINEA, porStock);
    }

    /**
     * Precio unitario de una presentación.
     *
     * - Sin precio propio (NULL, caso de la caja): es el precio efectivo del producto, con su oferta si la tiene.
     *   Así la caja nunca se desfasa si cambias el precio del producto.
     * - Con precio propio (el blíster): si el producto está en oferta, se le aplica el mismo porcentaje de descuento
     *   que al producto, para que una oferta alcance a todas sus presentaciones.
     */
    public static BigDecimal precioUnitario(BigDecimal precioPresentacion, BigDecimal precioVentaProducto,
                                            BigDecimal precioConDescuentoProducto) {
        if (precioPresentacion == null) {
            return precioConDescuentoProducto != null ? precioConDescuentoProducto : precioVentaProducto;
        }
        boolean hayOferta = precioVentaProducto != null && precioVentaProducto.signum() > 0
                && precioConDescuentoProducto != null
                && precioConDescuentoProducto.compareTo(precioVentaProducto) < 0;
        if (!hayOferta) {
            return precioPresentacion;
        }
        BigDecimal proporcion = precioConDescuentoProducto.divide(precioVentaProducto, 6, RoundingMode.HALF_UP);
        return precioPresentacion.multiply(proporcion).setScale(2, RoundingMode.HALF_UP);
    }

    /** Precio SIN oferta de una presentación (el que se muestra tachado cuando hay descuento). */
    public static BigDecimal precioRegular(BigDecimal precioPresentacion, BigDecimal precioVentaProducto) {
        return precioPresentacion != null ? precioPresentacion : precioVentaProducto;
    }
}
