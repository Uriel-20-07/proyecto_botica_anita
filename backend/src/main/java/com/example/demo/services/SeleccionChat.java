package com.example.demo.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.example.demo.dto.chatbot.ProductoChatDTO;

/**
 * Elige qué tarjetas mostrar en el chat a partir de los candidatos que devolvió la base de datos (ya ordenados por
 * relevancia). Son funciones puras, sin Spring ni base de datos, para poder probarlas fácilmente.
 *
 * - Forma farmacéutica: si el cliente pidió pastillas, no se le sugieren jarabes ni cremas (y viceversa).
 * - Variedad: primero un producto por marca y por línea de tallas; si faltan tarjetas, se completan con el resto.
 */
public final class SeleccionChat {

    /** Formas farmacéuticas que se distinguen al sugerir productos. */
    public enum Forma {
        SOLIDA(Pattern.compile("\\b(pastill|tablet|comprimid|capsul|grage|pildor|caramel)\\w*")),
        LIQUIDA(Pattern.compile("\\b(jarab|suspension|gota|elixir|solucion oral)\\w*")),
        TOPICA(Pattern.compile("\\b(crem|unguent|pomad|locion|balsam)\\w*|\\bgel(es)?\\b")),
        /** Otras presentaciones (spray, sobres, inhalador...): no se sugieren cuando el cliente pidió otra forma. */
        OTRA(Pattern.compile("\\b(spray|sobre|inhalador|ampolla|inyectable|parche|supositorio|ovulo|nebuliz)\\w*"));

        private final Pattern palabras;

        Forma(Pattern palabras) {
            this.palabras = palabras;
        }

        boolean aparece(String textoNormalizado) {
            return palabras.matcher(textoNormalizado).find();
        }
    }

    private SeleccionChat() {
    }

    /**
     * Forma que pidió el cliente. Primero se usa lo que indicó SofIA ("solida", "liquida", "topica"); si no indicó
     * nada, se deduce de los términos de búsqueda. Devuelve null si no pidió una forma concreta (o pidió varias).
     */
    public static Forma formaPedida(String indicada, Collection<String> terminos) {
        String f = TextoChat.sinAcentos(indicada).trim();
        if (f.startsWith("solid")) {
            return Forma.SOLIDA;
        }
        if (f.startsWith("liquid")) {
            return Forma.LIQUIDA;
        }
        if (f.startsWith("topic")) {
            return Forma.TOPICA;
        }
        if (terminos == null || terminos.isEmpty()) {
            return null;
        }
        String texto = TextoChat.sinAcentos(String.join(" ", terminos.stream().filter(Objects::nonNull).toList()));
        Forma encontrada = null;
        for (Forma forma : Forma.values()) {
            if (forma.aparece(texto)) {
                if (encontrada != null) {
                    return null; // pidió más de una forma: no se filtra
                }
                encontrada = forma;
            }
        }
        return encontrada;
    }

    /** Forma farmacéutica que se lee en el nombre del producto, o null si el nombre no la dice (o dice varias). */
    public static Forma formaDe(String nombre) {
        String texto = TextoChat.sinAcentos(nombre);
        Forma encontrada = null;
        for (Forma forma : Forma.values()) {
            if (forma.aparece(texto)) {
                if (encontrada != null) {
                    return null;
                }
                encontrada = forma;
            }
        }
        return encontrada;
    }

    /**
     * Elige hasta "limite" productos de "candidatos" (en orden de relevancia).
     *
     * 1. Si se pidió una forma, van primero los productos de esa forma y luego los que no dicen su forma; los de
     *    otra forma se descartan. Si no queda ninguno, se usan todos los candidatos (mejor algo que nada).
     * 2. No se repite una línea de tallas.
     * 3. Se prefiere una tarjeta por marca; si no alcanza, se completa con más productos de las mismas marcas.
     */
    public static List<ProductoChatDTO> elegir(List<ProductoChatDTO> candidatos, Forma pedida, int limite) {
        if (candidatos == null || candidatos.isEmpty() || limite <= 0) {
            return List.of();
        }

        List<ProductoChatDTO> ordenados = new ArrayList<>(candidatos.size());
        if (pedida != null) {
            List<ProductoChatDTO> sinForma = new ArrayList<>();
            for (ProductoChatDTO p : candidatos) {
                Forma forma = formaDe(p.getNombre());
                if (forma == pedida) {
                    ordenados.add(p);
                } else if (forma == null) {
                    sinForma.add(p);
                }
            }
            ordenados.addAll(sinForma);
        }
        if (ordenados.isEmpty()) {
            ordenados.addAll(candidatos);
        }

        // Sin líneas de tallas repetidas
        List<ProductoChatDTO> unicos = new ArrayList<>(ordenados.size());
        Set<String> lineas = new HashSet<>();
        for (ProductoChatDTO p : ordenados) {
            if (p.getGrupoVariantes() == null || lineas.add(p.getGrupoVariantes())) {
                unicos.add(p);
            }
        }

        // Primera pasada: una por marca. Segunda: se completa con lo que quede, respetando el orden.
        List<ProductoChatDTO> elegidos = new ArrayList<>(limite);
        Set<Integer> marcas = new HashSet<>();
        for (ProductoChatDTO p : unicos) {
            if (elegidos.size() >= limite) {
                break;
            }
            if (p.getIdMarca() == null || marcas.add(p.getIdMarca())) {
                elegidos.add(p);
            }
        }
        for (ProductoChatDTO p : unicos) {
            if (elegidos.size() >= limite) {
                break;
            }
            if (!elegidos.contains(p)) {
                elegidos.add(p);
            }
        }

        // Se devuelven en el orden de relevancia original (disponibles primero, más coincidencias primero...)
        List<ProductoChatDTO> resultado = new ArrayList<>(elegidos.size());
        for (ProductoChatDTO p : unicos) {
            if (elegidos.contains(p)) {
                resultado.add(p);
            }
        }
        return resultado;
    }
}
