package com.example.demo.dto.chatbot;

/**
 * Paso del flujo guiado en el que se encuentra el cliente.
 *
 * El backend decide cuál es el siguiente paso según los filtros que ya eligió el cliente:
 * CATEGORIA → SUBCATEGORIA → MARCA → PRODUCTOS. Los pasos que tienen una sola opción
 * (y que no esconden productos) se saltan automáticamente.
 */
public enum PasoChatbot {
    CATEGORIA,
    SUBCATEGORIA,
    MARCA,
    PRODUCTOS,
    SIN_RESULTADOS
}
