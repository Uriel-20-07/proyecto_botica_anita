package com.example.demo.dto.chatbot;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Una opción que el cliente puede tocar en el chat (una categoría, subcategoría o marca).
 *
 * Se construye directamente desde la consulta JPQL ("select new ..."), de modo que la base de
 * datos solo devuelve estos cuatro valores y no entidades completas.
 */
@Getter
@AllArgsConstructor
public class OpcionDTO {

    private final Integer id;
    private final String nombre;

    /** Productos disponibles (con stock y no vencidos) dentro de esta opción. */
    private final Long total;

    /** Cuántos de esos productos tienen una oferta vigente. */
    private final Long totalOfertas;
}
