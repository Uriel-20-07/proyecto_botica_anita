package com.example.demo.dto;

/**
 * Conteo de productos de una subcategoría o una marca.
 *
 * Se usa en GET /api/productos/conteos para que el menú lateral
 * del catálogo muestre cantidades reales calculadas en la BD
 * (no sobre la página de 30 productos cargada en memoria).
 *
 * @param id       id de la subcategoría o de la marca.
 * @param nombre   nombre de la subcategoría o de la marca.
 * @param cantidad cantidad de productos que la tienen.
 */
public record ConteoDTO(
        Integer id,
        String nombre,
        Long cantidad
) {
}
