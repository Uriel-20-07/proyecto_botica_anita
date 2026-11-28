package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.dto.chatbot.ProductoChatDTO;
import com.example.demo.services.SeleccionChat.Forma;

class SeleccionChatTest {

    private static ProductoChatDTO producto(int id, String nombre, Integer marca, String linea) {
        return new ProductoChatDTO(id, nombre, BigDecimal.TEN, null, false, 50, null, null, marca, linea);
    }

    private static List<Integer> ids(List<ProductoChatDTO> productos) {
        return productos.stream().map(ProductoChatDTO::getIdProducto).toList();
    }

    @Test
    void deduceLaFormaDeLosTerminosODeLoQueIndicoSofia() {
        assertEquals(Forma.SOLIDA, SeleccionChat.formaPedida(null, List.of("pastillas para la garganta", "garganta")));
        assertEquals(Forma.LIQUIDA, SeleccionChat.formaPedida(null, List.of("jarabe para la tos")));
        assertEquals(Forma.TOPICA, SeleccionChat.formaPedida("tópica", List.of("dolor muscular")));
        assertNull(SeleccionChat.formaPedida(null, List.of("dolor de garganta")));
        assertNull(SeleccionChat.formaPedida(null, List.of("pastillas o jarabe")));
    }

    @Test
    void siPidioPastillasNoSugiereJarabesNiUnguentos() {
        List<ProductoChatDTO> candidatos = List.of(
                producto(1, "Strepsils Miel y Limon x 24 pastillas", 1, null),
                producto(2, "Mentholatum Unguento 30 g", 2, null),
                producto(3, "Pastillas Valda Mentol", 3, null),
                producto(4, "Jarabe para la tos Vick 120 ml", 4, null),
                producto(5, "Oximetazolina Spray Nasal 15 ml", 5, null),
                producto(6, "Antiséptico bucal Mentol", 6, null));

        List<ProductoChatDTO> elegidos = SeleccionChat.elegir(candidatos, Forma.SOLIDA, 5);

        // Primero los que son pastillas; luego los que no dicen su forma. Nunca el jarabe, el ungüento ni el spray.
        assertEquals(List.of(1, 3, 6), ids(elegidos));
    }

    @Test
    void siNoHayNadaDeLaFormaPedidaMuestraLoQueHay() {
        List<ProductoChatDTO> candidatos = List.of(producto(1, "Jarabe Vick 120 ml", 1, null));
        assertEquals(List.of(1), ids(SeleccionChat.elegir(candidatos, Forma.SOLIDA, 5)));
    }

    @Test
    void unaTarjetaPorLineaDeTallasYPrefiereMarcasDistintas() {
        List<ProductoChatDTO> candidatos = List.of(
                producto(1, "Pañales Tena Slip M", 10, "tena-slip"),
                producto(2, "Pañales Tena Slip G", 10, "tena-slip"),
                producto(3, "Pañales Tena Pants M", 10, "tena-pants"),
                producto(4, "Pañales Plenitud Classic G", 20, "plenitud-classic"),
                producto(5, "Pañales Tena Slip XG", 10, "tena-slip"),
                producto(6, "Pañales Cotidian G", 30, null));

        List<ProductoChatDTO> elegidos = SeleccionChat.elegir(candidatos, null, 3);

        // Tena, Plenitud y Cotidian antes que una segunda línea de Tena; ninguna talla repetida
        assertEquals(List.of(1, 4, 6), ids(elegidos));
    }

    @Test
    void completaConLaMismaMarcaSiNoHayOtras() {
        List<ProductoChatDTO> candidatos = List.of(
                producto(1, "Tena Slip M", 10, "tena-slip"),
                producto(2, "Tena Slip G", 10, "tena-slip"),
                producto(3, "Tena Pants M", 10, "tena-pants"));

        assertEquals(List.of(1, 3), ids(SeleccionChat.elegir(candidatos, null, 5)));
    }
}
