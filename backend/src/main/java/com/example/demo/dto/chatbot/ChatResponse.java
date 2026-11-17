package com.example.demo.dto.chatbot;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Respuesta del chatbot a un mensaje de texto libre.
 *
 * Solo uno de estos casos suele venir lleno además de "content":
 * - functionCall: acción que debe ejecutar el navegador (agregarAlCarrito, redirigir).
 * - productos: tarjetas de productos encontradas por la búsqueda.
 * - paso: el flujo guiado, ofrecido como alternativa cuando no se encontró nada o hubo un problema.
 */
@Getter
@Setter
public class ChatResponse {

    private String role = "model";
    private String content;
    private Map<String, Object> functionCall;
    private List<ProductoChatDTO> productos;
    private ChatbotPasoResponse paso;

    public static ChatResponse texto(String contenido) {
        ChatResponse r = new ChatResponse();
        r.content = contenido;
        return r;
    }
}
