package com.example.demo.dto.chatbot;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Respuesta del flujo guiado: la pregunta amable que SofIA le hace al cliente y lo que puede tocar.
 *
 * - En los pasos CATEGORIA, SUBCATEGORIA y MARCA viene "opciones".
 * - En el paso PRODUCTOS viene "productos" (y "hayMas" si existen más resultados).
 * - "filtros" refleja lo que quedó aplicado, incluidas las opciones que el backend eligió solo
 *   por ser la única posible, para que el frontend mantenga sus chips sincronizados.
 */
@Getter
@Builder
public class ChatbotPasoResponse {

    private final PasoChatbot paso;
    private final String pregunta;
    private final List<OpcionDTO> opciones;
    private final List<ProductoChatDTO> productos;
    private final boolean hayMas;
    private final ChatbotFiltros filtros;
}
