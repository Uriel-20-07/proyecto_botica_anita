package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Respuesta de GET /api/productos/conteos.
 *
 * - subcategorias: cuántos productos tiene cada subcategoría (0 si no aparece).
 * - marcas:        cuántos productos tiene cada marca, opcionalmente
 *                  restringido a una categoría con ?categoriaId=.
 * - precioMaximo:  precio más alto del catálogo completo (efectivo), para
 *                  que el slider "Precio Máximo" del frontend tenga un tope real.
 */
public record ConteosDTO(
        List<ConteoDTO> subcategorias,
        List<ConteoDTO> marcas,
        BigDecimal precioMaximo
) {
}
