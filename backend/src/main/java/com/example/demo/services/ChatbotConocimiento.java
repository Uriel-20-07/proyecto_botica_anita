package com.example.demo.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Información oficial de la botica (contacto, horarios, envíos, pagos, devoluciones, recetas) que SofIA
 * usa para responder preguntas frecuentes sin inventar datos.
 *
 * Se lee una sola vez al arrancar desde src/main/resources/chatbot/faq.md y se agrega al system prompt.
 * Para cambiar lo que SofIA sabe basta con editar ese archivo; no hay que tocar código Java.
 *
 * Si el archivo no existe, el chatbot sigue funcionando, pero sin esa información (lo avisa en el log).
 */
@Component
public class ChatbotConocimiento {

    private static final Logger log = LoggerFactory.getLogger(ChatbotConocimiento.class);

    private final String faq;

    public ChatbotConocimiento(@Value("classpath:chatbot/faq.md") Resource recurso) {
        this.faq = cargar(recurso);
    }

    /** Texto de las preguntas frecuentes, o una cadena vacía si no se pudo cargar. */
    public String getFaq() {
        return faq;
    }

    private static String cargar(Resource recurso) {
        try (InputStream in = recurso.getInputStream()) {
            String texto = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "").replace("\r\n", "\n").trim();
            log.info("[Chatbot] Información oficial cargada ({} caracteres).", texto.length());
            return texto;
        } catch (IOException e) {
            log.warn("[Chatbot] No se pudo leer chatbot/faq.md: SofIA responderá sin la información de la botica. {}",
                    e.getMessage());
            return "";
        }
    }
}
