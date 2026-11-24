package com.example.demo.dto;

import java.time.LocalDateTime;

/**
 * Producto cuya reserva de 2 horas venció y fue liberada.
 * Se devuelve en el carrito para que el frontend avise al usuario.
 */
public record ItemExpiradoDTO(
        Integer idProducto,
        String nombre,
        Integer cantidad,
        LocalDateTime fechaExpiracion,
        String mensaje
) {
}
