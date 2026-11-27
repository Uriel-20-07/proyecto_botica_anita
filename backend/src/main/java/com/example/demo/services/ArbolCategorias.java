package com.example.demo.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Categorías y subcategorías de la tienda, tal como existen hoy en la base de datos.
 *
 * Sirve para tres cosas:
 * 1) Mostrarle a SofIA qué secciones tiene la tienda, para que busque solo donde corresponde.
 * 2) Traducir lo que SofIA pide ("Dolor", "gripe y tos") a los nombres reales de la tienda.
 * 3) Garantizar que una búsqueda por síntoma solo mire en las categorías de salud (por ejemplo, MEDICAMENTOS)
 *    y nunca muestre un biberón o un perfume como respuesta a un dolor de estómago.
 *
 * Es una clase pura (sin Spring ni base de datos) para poder probarla fácilmente. Todos los nombres "normalizados"
 * están en minúsculas y sin tildes (ver TextoChat.sinAcentos).
 */
public final class ArbolCategorias {

    /** Una categoría con sus subcategorías, con los nombres originales de la base de datos. */
    public record Categoria(String nombre, List<String> subcategorias) { }

    /** Si un nombre pedido coincide con más de estas opciones es demasiado ambiguo y se ignora. */
    private static final int MAX_COINCIDENCIAS = 4;

    private static final Set<String> RELLENO = Set.of("del", "los", "las", "con", "por", "para");
    private static final ArbolCategorias VACIO = new ArbolCategorias(List.of());

    private final List<Categoria> categorias;
    private final Set<String> categoriasNormalizadas = new LinkedHashSet<>();
    private final Set<String> todosNormalizados = new LinkedHashSet<>();
    private final Map<String, String> padreDe = new HashMap<>();

    private ArbolCategorias(List<Categoria> categorias) {
        this.categorias = categorias;
        for (Categoria c : categorias) {
            String cat = TextoChat.sinAcentos(c.nombre()).trim();
            categoriasNormalizadas.add(cat);
            todosNormalizados.add(cat);
            for (String sub : c.subcategorias()) {
                String s = TextoChat.sinAcentos(sub).trim();
                todosNormalizados.add(s);
                padreDe.put(s, cat);
            }
        }
    }

    public static ArbolCategorias vacio() {
        return VACIO;
    }

    /**
     * Arma el árbol a partir de filas [nombre de categoría, nombre de subcategoría]. La subcategoría puede ser null
     * (productos sin subcategoría). Se ignoran filas vacías y repetidas.
     */
    public static ArbolCategorias desdeFilas(Collection<Object[]> filas) {
        Map<String, List<String>> porCategoria = new LinkedHashMap<>();
        if (filas != null) {
            for (Object[] fila : filas) {
                if (fila == null || fila.length < 2 || !(fila[0] instanceof String cat) || cat.isBlank()) {
                    continue;
                }
                List<String> subs = porCategoria.computeIfAbsent(cat.trim(), k -> new ArrayList<>());
                if (fila[1] instanceof String sub && !sub.isBlank() && !subs.contains(sub.trim())) {
                    subs.add(sub.trim());
                }
            }
        }
        List<Categoria> lista = new ArrayList<>();
        porCategoria.forEach((nombre, subs) -> lista.add(new Categoria(nombre, List.copyOf(subs))));
        return new ArbolCategorias(List.copyOf(lista));
    }

    public boolean estaVacio() {
        return categorias.isEmpty();
    }

    /**
     * Traduce los nombres que pidió SofIA a nombres reales de la tienda (normalizados).
     *
     * - Un nombre exacto (sin importar mayúsculas ni tildes) se acepta tal cual.
     * - Si no, se acepta el nombre cuyas palabras incluyen todas las pedidas ("gripe y tos" → "Gripe, tos y garganta").
     * - Los nombres inventados o demasiado ambiguos se descartan.
     */
    public List<String> resolver(Collection<String> pedidos) {
        Set<String> salida = new LinkedHashSet<>();
        if (pedidos == null) {
            return List.of();
        }
        for (String pedido : pedidos) {
            String normalizado = TextoChat.sinAcentos(pedido).trim();
            if (normalizado.isEmpty()) {
                continue;
            }
            if (todosNormalizados.contains(normalizado)) {
                salida.add(normalizado);
                continue;
            }
            Set<String> buscadas = palabras(normalizado);
            if (buscadas.isEmpty()) {
                continue;
            }
            List<String> coincidencias = new ArrayList<>();
            for (String candidato : todosNormalizados) {
                if (palabras(candidato).containsAll(buscadas)) {
                    coincidencias.add(candidato);
                }
            }
            if (!coincidencias.isEmpty() && coincidencias.size() <= MAX_COINCIDENCIAS) {
                salida.addAll(coincidencias);
            }
        }
        return List.copyOf(salida);
    }

    /**
     * Categorías a las que pertenecen las subcategorías indicadas, para ampliar una búsqueda un nivel
     * ("digestivo" → "medicamentos"). No incluye las que ya estaban pedidas.
     */
    public List<String> padresDe(Collection<String> normalizados) {
        Set<String> padres = new LinkedHashSet<>();
        for (String n : normalizados) {
            String padre = padreDe.get(n);
            if (padre != null) {
                padres.add(padre);
            }
        }
        padres.removeAll(normalizados);
        return List.copyOf(padres);
    }

    /**
     * Limita lo pedido a las categorías de salud. Lo que cae fuera (por ejemplo "bebé") se descarta, y si no queda
     * nada se usan las categorías de salud completas. Si las categorías de salud configuradas no existen en la
     * tienda, no se puede restringir y se devuelve lo pedido sin cambios.
     */
    public List<String> dentroDeSalud(Collection<String> pedidas, Collection<String> categoriasSalud) {
        Set<String> salud = new LinkedHashSet<>();
        if (categoriasSalud != null) {
            for (String s : categoriasSalud) {
                String n = TextoChat.sinAcentos(s).trim();
                if (categoriasNormalizadas.contains(n)) {
                    salud.add(n);
                }
            }
        }
        if (salud.isEmpty()) {
            return List.copyOf(pedidas);
        }
        List<String> dentro = pedidas.stream()
                .filter(n -> salud.contains(n) || salud.contains(padreDe.get(n)))
                .toList();
        return dentro.isEmpty() ? List.copyOf(salud) : dentro;
    }

    /** Texto para el prompt: una línea por categoría con sus subcategorías. */
    public String comoTexto() {
        StringBuilder sb = new StringBuilder();
        for (Categoria c : categorias) {
            sb.append("- ").append(c.nombre());
            if (!c.subcategorias().isEmpty()) {
                sb.append(": ").append(String.join("; ", c.subcategorias()));
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static Set<String> palabras(String normalizado) {
        Set<String> palabras = new LinkedHashSet<>();
        for (String p : normalizado.split("[^\\p{L}\\p{N}]+")) {
            if (p.length() >= 3 && !RELLENO.contains(p)) {
                palabras.add(p);
            }
        }
        return palabras;
    }
}
