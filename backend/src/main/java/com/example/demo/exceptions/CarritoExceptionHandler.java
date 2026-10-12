package com.example.demo.exceptions;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Convierte las CarritoException de negocio en respuestas HTTP 400 con un
 * cuerpo ESTRUCTURADO que el frontend puede interpretar:
 *
 *     { "error": "mensaje legible", "maxAdicional": 4 }
 *
 * De esta forma, tanto la tienda como el chatbot pueden mostrar el motivo del
 * rechazo y, cuando maxAdicional > 0, ofrecer agregar exactamente esa cantidad.
 *
 * @RestControllerAdvice es detectado por el component scan de Spring Boot
 * (paquete com.example.demo) y aplica a todos los controladores.
 */
@RestControllerAdvice
public class CarritoExceptionHandler {

    @ExceptionHandler(CarritoException.class)
    public ResponseEntity<Map<String, Object>> manejarCarritoException(CarritoException ex) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", ex.getMessage());
        cuerpo.put("maxAdicional", ex.getMaxAdicional());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }
}
