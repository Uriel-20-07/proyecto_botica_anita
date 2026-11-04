package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Promoción de la botica lista para mostrarse en la web.
 *
 * Una promoción se compone de uno o más productos ({@link ItemPromo}) y una regla:
 * - unidadesGratis > 0  → "lleva X y paga menos" (ej. 3x2: unidadesGratis = 1).
 * - porcentaje > 0      → descuento porcentual sobre el conjunto.
 *
 * Los precios (precioNormal / precioPromo / ahorro) son por UNA vez la promoción
 * (ej. 3 pañales, o el combo de 3 productos).
 */
public record PromocionDTO(
        String id,
        String titulo,
        String etiqueta,
        String descripcion,
        int unidadesGratis,
        double porcentaje,
        List<ItemPromo> items,
        BigDecimal precioNormal,
        BigDecimal precioPromo,
        BigDecimal ahorro) {

    /** Producto que forma parte de la promoción y cuántas unidades se necesitan. */
    public record ItemPromo(
            Integer idProducto,
            String nombre,
            String imgUrl,
            BigDecimal precioUnitario,
            int cantidad) {
    }
}
