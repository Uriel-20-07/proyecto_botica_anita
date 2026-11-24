package com.example.demo.models;

/**
 * Estado de una línea del carrito.
 *
 * - ACTIVA: la reserva de stock está vigente.
 * - EXPIRADA: pasaron las 2 horas; el stock ya se liberó y la línea se
 *   elimina en el siguiente GET del carrito (notificando al usuario).
 * - CONFIRMADA / LIBERADA: estados de apoyo para transiciones; las líneas
 *   confirmadas o liberadas se eliminan del carrito.
 */
public enum EstadoDetalleCarrito {
    ACTIVA,
    EXPIRADA,
    CONFIRMADA,
    LIBERADA
}
