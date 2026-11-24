package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Filtros opcionales del catálogo paginado (GET /api/productos/paginado).
 *
 * Todos los campos son opcionales: un filtro en null o vacío simplemente
 * no agrega condición, así que la misma llamada sirve para "todo el catálogo"
 * y para "solo los productos de una categoría con precio hasta X".
 *
 * @param categoriaId     id de la categoría (null = todas).
 * @param subcategoriaIds ids de subcategorías marcadas (vacío = todas).
 * @param marcaIds        ids de marcas marcadas (vacío = todas).
 * @param search          texto de búsqueda (nombre, descripción o categoría).
 * @param enOferta        true = solo productos en oferta vigente.
 * @param precioMax       precio efectivo máximo (null = sin tope).
 * @param orden           relevancia | precio_asc | precio_desc | nombre_asc.
 */
public record FiltrosCatalogo(
        Integer categoriaId,
        List<Integer> subcategoriaIds,
        List<Integer> marcaIds,
        String search,
        Boolean enOferta,
        BigDecimal precioMax,
        String orden
) {
}
