package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Pruebas de las utilidades de texto del chatbot (búsqueda sin tildes ni plurales, y detección de promesas). */
class TextoChatTest {

    // ---------- patrones de búsqueda ----------

    @Test
    void ignoraMayusculasTildesYPalabrasDeRelleno() {
        assertEquals(List.of("%dolor%cabeza%"), TextoChat.patrones(List.of("Dolor de Cabeza"), 8));
        assertEquals(List.of("%congestion%nasal%"), TextoChat.patrones(List.of("congestión nasal"), 8));
    }

    @Test
    void pasaLosPluralesASingular() {
        assertEquals(List.of("%analgesico%"), TextoChat.patrones(List.of("analgésicos"), 8));
        assertEquals(List.of("%dolor%"), TextoChat.patrones(List.of("dolores"), 8));
        assertEquals(List.of("%vitamina%"), TextoChat.patrones(List.of("Vitaminas"), 8));
    }

    @Test
    void laEnieSeTrataComoNPorqueLaBaseDeDatosTambienLaNormaliza() {
        assertEquals(List.of("%nino%"), TextoChat.patrones(List.of("niños"), 8));
    }

    @Test
    void unaFormaFarmaceuticaSolaSeAmpliaASusEquivalentes() {
        // El caso real: el cliente dijo "Pastillas" y los productos dicen "tabletas"
        List<String> patrones = TextoChat.patrones(List.of("Pastillas"), 8);
        assertTrue(patrones.contains("%pastilla%"));
        assertTrue(patrones.contains("%tableta%"));
        assertTrue(patrones.contains("%comprimido%"));
        assertTrue(patrones.contains("%capsula%"));
    }

    @Test
    void unaFraseConFormaTambienSeBuscaSinLaForma() {
        List<String> patrones = TextoChat.patrones(List.of("pastillas para el dolor de cabeza"), 8);
        assertTrue(patrones.contains("%pastilla%dolor%cabeza%"));
        assertTrue(patrones.contains("%dolor%cabeza%"));
    }

    @Test
    void variosTerminosDanVariosPatronesSinRepetir() {
        List<String> patrones = TextoChat.patrones(
                List.of("analgésico", "paracetamol", "dolor de cabeza", "Analgésicos"), 8);
        assertEquals(List.of("%analgesico%", "%paracetamol%", "%dolor%cabeza%"), patrones);
    }

    @Test
    void losSignosYComodinesDelClienteNoLlegaranALaConsulta() {
        // % y _ son comodines de LIKE: no deben poder colarse desde lo que escribe el cliente
        assertEquals(List.of("%100%natural%"), TextoChat.patrones(List.of("100% natural_"), 8));
        assertEquals(List.of(), TextoChat.patrones(List.of("%%", "__", "  ", "a"), 8));
    }

    @Test
    void respetaElMaximoDePatronesYLosNulos() {
        assertEquals(2, TextoChat.patrones(List.of("uno1", "dos2", "tres3", "cuatro4"), 2).size());
        assertEquals(List.of(), TextoChat.patrones(null, 8));
        assertEquals(List.of("%gripe%"), TextoChat.patrones(java.util.Arrays.asList(null, "gripe"), 8));
    }

    // ---------- promesas de mostrar productos sin llamar a la función ----------

    @Test
    void detectaLasFrasesExactasDeLaConversacionQueFallo() {
        assertTrue(TextoChat.prometeMostrarProductos("¡Claro que sí! Te muestro las opciones disponibles 😊"));
        assertTrue(TextoChat.prometeMostrarProductos("¡Claro que sí! Aquí tienes las opciones para el dolor de cabeza 😊"));
        assertTrue(TextoChat.prometeMostrarProductos("Aquí están los productos que encontré"));
    }

    @Test
    void noConfundeRespuestasNormalesConPromesas() {
        assertFalse(TextoChat.prometeMostrarProductos("Aquí tienes la información de envíos: el delivery cuesta S/ 5.00"));
        assertFalse(TextoChat.prometeMostrarProductos("Lamento mucho que te sientas así 💙. ¿El dolor es leve, moderado o muy fuerte?"));
        assertFalse(TextoChat.prometeMostrarProductos("Para ayudarte, ¿prefieres pastillas, jarabe o un spray nasal? 😊"));
        assertFalse(TextoChat.prometeMostrarProductos(""));
        assertFalse(TextoChat.prometeMostrarProductos(null));
    }

    // ---------- limpieza de mensajes ----------

    @Test
    void limpiarMensajeQuitaEtiquetasSaltosYRecortaElLargo() {
        assertEquals("Hola mundo", TextoChat.limpiarMensaje("<b>Hola</b>\n  mundo", 50));
        assertEquals("", TextoChat.limpiarMensaje(null, 50));
        assertEquals(5, TextoChat.limpiarMensaje("abcdefghij", 5).length());
    }
}
