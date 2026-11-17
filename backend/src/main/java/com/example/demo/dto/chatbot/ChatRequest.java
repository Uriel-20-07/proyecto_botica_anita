package com.example.demo.dto.chatbot;

import java.util.List;

import lombok.Data;

/**
 * Petición de texto libre al chatbot.
 *
 * - messages: los últimos mensajes de la conversación escrita.
 * - filtros: lo que el cliente ya eligió con los botones (puede ser null), para que SofIA lo tenga en
 *   cuenta y las búsquedas se limiten a esa categoría, subcategoría o marca.
 * - yaMostrados: productos que ya se le mostraron como tarjetas (puede ser null o vacío).
 */
@Data
public class ChatRequest {
    private List<ChatMessage> messages;
    private ChatbotFiltros filtros;
    private List<ProductoMostrado> yaMostrados;
}
