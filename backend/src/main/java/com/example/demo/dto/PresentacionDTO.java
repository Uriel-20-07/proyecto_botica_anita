package com.example.demo.dto;

import java.math.BigDecimal;

/**
 * Presentación de un producto lista para mostrarse (selector de blíster / caja).
 *
 * @param precio         lo que paga el cliente por UNA unidad de esta presentación (con la oferta del producto, si la hay).
 * @param precioRegular  precio sin oferta (para mostrarlo tachado cuando "precio" es menor).
 * @param disponible     false si el stock actual no alcanza ni para una unidad de esta presentación.
 * @param maxPorPedido   máximo que se puede pedir de una vez (el menor entre el stock y el límite de 10).
 */
public record PresentacionDTO(
        Integer idPresentacion,
        String etiqueta,
        Integer unidades,
        BigDecimal precio,
        BigDecimal precioRegular,
        boolean esPredeterminada,
        boolean disponible,
        int maxPorPedido) {
}
