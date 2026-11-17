package com.example.demo.dto.chatbot;

import lombok.Data;

/**
 * Un producto que ya se le mostró al cliente como tarjeta en el chat.
 *
 * El frontend los envía junto con los mensajes escritos. Sirven para dos cosas:
 * - Cuando el cliente pide "otras opciones", el backend los excluye de la búsqueda.
 * - SofIA sabe qué productos tiene el cliente a la vista, para mantener el hilo de la conversación.
 */
@Data
public class ProductoMostrado {
    private Integer idProducto;
    private String nombre;
}
