package com.example.demo.dto;

import java.math.BigDecimal;

/**
 * Una talla de una línea de producto (por ejemplo "Pañales Huggies Active Sec", talla G), lista para el selector del detalle.
 *
 * @param idProducto     producto de esa talla (cada talla es un producto con su propio stock).
 * @param etiqueta       talla: "RN", "P", "M", "G", "XG"...
 * @param nombre         nombre completo del producto de esa talla.
 * @param precio         precio que paga el cliente (con la oferta, si la hay).
 * @param precioRegular  precio sin oferta (para mostrarlo tachado).
 * @param disponible     false si no hay stock vendible de esa talla (agotada o vencida).
 * @param imgUrl         imagen del producto de esa talla.
 */
public record VarianteDTO(
        Integer idProducto,
        String etiqueta,
        String nombre,
        BigDecimal precio,
        BigDecimal precioRegular,
        boolean disponible,
        String imgUrl) {
}
