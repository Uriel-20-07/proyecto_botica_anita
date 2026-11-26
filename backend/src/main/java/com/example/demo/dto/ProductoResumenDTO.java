package com.example.demo.dto;

import java.math.BigDecimal;

/**
 * DTO ligero para el catálogo y listados de productos.
 * Solo trae los campos necesarios para la vista, evitando consultas N+1
 * gracias a @EntityGraph en el repositorio.
 * Los nombres de relaciones vienen como String para el EntityGraph,
 * y el frontend los mapea a sus interfaces internas.
 */
public record ProductoResumenDTO(
    Integer idProducto,
    String nombre,
    BigDecimal precioVenta,
    BigDecimal precioOferta,
    String imgUrl,
    String categoriaNombre,
    String subcategoriaNombre,
    String marcaNombre,
    Boolean enOferta,
    Integer stock
) {
    // Constructor para mapear desde el EntityGraph query
    public static ProductoResumenDTO of(
        Integer idProducto,
        String nombre,
        BigDecimal precioVenta,
        BigDecimal precioOferta,
        String imgUrl,
        String categoriaNombre,
        String subcategoriaNombre,
        String marcaNombre,
        Boolean enOferta,
        Integer stock
    ) {
        return new ProductoResumenDTO(idProducto, nombre, precioVenta, precioOferta, imgUrl, categoriaNombre, subcategoriaNombre, marcaNombre, enOferta, stock);
    }
}