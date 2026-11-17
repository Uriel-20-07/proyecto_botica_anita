package com.example.demo.services;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utilidades de texto del chatbot. Son funciones puras (sin Spring ni base de datos) para poder probarlas
 * fácilmente.
 */
public final class TextoChat {

    private static final int MAX_LARGO_TERMINO = 40;

    /** Palabras de relleno que no ayudan a buscar productos. */
    private static final Set<String> RELLENO = Set.of(
            "de", "del", "la", "el", "los", "las", "para", "con", "y", "en", "un", "una", "por", "al", "o", "mi", "su");

    /** Formas farmacéuticas que el cliente suele nombrar de distintas maneras ("pastillas" = "tabletas"). */
    private static final List<String> FORMAS = List.of(
            "pastilla", "tableta", "comprimido", "capsula", "gragea", "pildora");

    /**
     * Detecta cuando el modelo anuncia que va a mostrar productos ("Te muestro las opciones...",
     * "Aquí tienes las alternativas...") pero no llamó a la función que realmente los muestra.
     * Exige que aparezca una palabra de producto para no confundirlo con frases como
     * "Aquí tienes la información de envíos".
     */
    private static final Pattern PROMETE_PRODUCTOS = Pattern.compile(
            "(?i)\\b(te muestro|te los muestro|te las muestro|aqu[ií] tienes|aqu[ií] est[aá]n|te presento|te dejo|d[eé]jame mostrarte)\\b"
                    + "[^.!?\\n]{0,80}\\b(opciones|productos|alternativas|medicamentos|pastillas|tabletas|jarabes|cremas)\\b");

    private TextoChat() {
    }

    /** Minúsculas y sin tildes ni diéresis. La "ñ" pasa a "n" (igual que en la consulta a la base de datos). */
    public static String sinAcentos(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    /**
     * Convierte los términos de búsqueda en patrones LIKE ya normalizados.
     *
     * - Ignora mayúsculas, tildes, signos y palabras de relleno ("dolor de cabeza" → "%dolor%cabeza%").
     * - Pasa plurales a singular ("analgésicos" → "%analgesico%", "dolores" → "%dolor%").
     * - Una forma farmacéutica sola se amplía a sus equivalentes ("pastillas" → pastilla, tableta, comprimido...).
     * - No repite patrones y devuelve como máximo "maximo".
     */
    public static List<String> patrones(Collection<String> terminos, int maximo) {
        Set<String> salida = new LinkedHashSet<>();
        if (terminos == null) {
            return List.of();
        }

        for (String termino : terminos) {
            if (termino == null) {
                continue;
            }
            String limpio = sinAcentos(termino).replaceAll("[^\\p{L}\\p{N} ]", " ").trim();
            if (limpio.length() > MAX_LARGO_TERMINO) {
                limpio = limpio.substring(0, MAX_LARGO_TERMINO);
            }

            List<String> palabras = new ArrayList<>();
            for (String palabra : limpio.split("\\s+")) {
                if (palabra.length() < 2 || RELLENO.contains(palabra)) {
                    continue;
                }
                palabras.add(singular(palabra));
            }
            if (palabras.isEmpty()) {
                continue;
            }

            if (palabras.size() == 1) {
                String unica = palabras.get(0);
                if (FORMAS.contains(unica)) {
                    for (String forma : FORMAS) {
                        salida.add("%" + forma + "%");
                    }
                } else if (unica.length() >= 3) {
                    salida.add("%" + unica + "%");
                }
            } else {
                salida.add("%" + String.join("%", palabras) + "%");
                // "pastillas para el dolor de cabeza": también se prueba sin la forma farmacéutica
                List<String> sinForma = palabras.stream().filter(p -> !FORMAS.contains(p)).toList();
                if (sinForma.size() < palabras.size() && !sinForma.isEmpty()) {
                    salida.add("%" + String.join("%", sinForma) + "%");
                }
            }

            if (salida.size() >= maximo) {
                break;
            }
        }
        return salida.stream().limit(maximo).toList();
    }

    /** Singular aproximado en español. Basta para búsquedas por coincidencia parcial. */
    static String singular(String palabra) {
        if (palabra.length() >= 6 && palabra.endsWith("es")) {
            return palabra.substring(0, palabra.length() - 2);
        }
        if (palabra.length() >= 4 && palabra.endsWith("s")) {
            return palabra.substring(0, palabra.length() - 1);
        }
        return palabra;
    }

    /** true si el texto anuncia que se mostrarán productos (ver PROMETE_PRODUCTOS). */
    public static boolean prometeMostrarProductos(String texto) {
        return texto != null && PROMETE_PRODUCTOS.matcher(texto).find();
    }

    /** Deja un texto breve de una sola línea y sin etiquetas HTML. Devuelve "" si no hay nada útil. */
    public static String limpiarMensaje(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        String limpio = texto.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        return limpio.length() > maximo ? limpio.substring(0, maximo).trim() : limpio;
    }
}
