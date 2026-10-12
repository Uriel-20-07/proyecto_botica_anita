package com.example.demo.exceptions;

/**
 * Excepción de NEGOCIO del carrito de compras.
 *
 * Se lanza cuando una operación sobre el carrito viola una regla de negocio:
 * cantidad inválida, límite de 10 unidades por producto o stock insuficiente.
 *
 * A diferencia de una RuntimeException genérica (que Spring convierte en un
 * HTTP 500 opaco), esta excepción transporta, además del mensaje, la cantidad
 * MÁXIMA ADICIONAL que el cliente sí puede agregar en este momento (maxAdicional).
 * El frontend (tienda o chatbot) usa ese valor para ofrecer, por ejemplo,
 * "¿deseas agregar N unidades?" cuando el cliente pidió de más.
 *
 * Un maxAdicional de 0 significa que no se puede agregar ninguna unidad más.
 */
public class CarritoException extends RuntimeException {

    /** Cantidad máxima adicional que el cliente puede agregar ahora mismo (>= 0). */
    private final int maxAdicional;

    public CarritoException(String mensaje, int maxAdicional) {
        super(mensaje);
        this.maxAdicional = maxAdicional;
    }

    public int getMaxAdicional() {
        return maxAdicional;
    }
}
