package com.example.demo.dto.chatbot;

import lombok.Data;

/**
 * Un mensaje del historial que el frontend envía cuando el cliente escribe con sus propias palabras.
 *
 * role: "user" o "model". Cualquier otro valor se descarta en el controlador.
 */
@Data
public class ChatMessage {
    private String role;
    private String content;
}
