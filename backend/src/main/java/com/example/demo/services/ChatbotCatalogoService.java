package com.example.demo.services;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.chatbot.ChatbotFiltros;
import com.example.demo.dto.chatbot.ChatbotPasoResponse;
import com.example.demo.dto.chatbot.OpcionDTO;
import com.example.demo.dto.chatbot.PasoChatbot;
import com.example.demo.dto.chatbot.ProductoChatDTO;
import com.example.demo.repositories.ChatbotBusquedaRepository;
import com.example.demo.repositories.ChatbotCatalogoRepository;

/**
 * Lógica del chatbot sobre el catálogo: el flujo guiado por botones y la búsqueda por texto.
 *
 * Flujo guiado (no usa IA, solo consultas pequeñas a la base de datos):
 *   categoría → subcategoría → marca → productos
 *
 * El cliente envía los filtros que lleva y el backend responde con el siguiente paso. Un paso con una
 * sola opción se salta solo si esa opción abarca todos los productos del nivel anterior; así nunca se
 * esconden productos que no tengan subcategoría o marca.
 *
 * Todos los métodos son de solo lectura y corren en una única transacción, de modo que los pasos que
 * necesitan varias consultas reutilizan la misma conexión.
 */
@Service
public class ChatbotCatalogoService {

    /** Productos que se muestran por "página" en el chat. */
    public static final int PRODUCTOS_POR_PAGINA = 5;

    private static final Logger log = LoggerFactory.getLogger(ChatbotCatalogoService.class);

    private static final int SIN_FILTRO = ChatbotCatalogoRepository.SIN_FILTRO;
    private static final int MAX_PATRONES = 8;
    private static final int MAX_MOSTRADOS = 30;
    private static final int MAX_LARGO_NOMBRE = 80;
    private static final int MAX_LARGO_BUSQUEDA = 60;
    private static final int MAX_PAGINA = 50;

    private final ChatbotCatalogoRepository repo;
    private final ChatbotBusquedaRepository busqueda;

    public ChatbotCatalogoService(ChatbotCatalogoRepository repo, ChatbotBusquedaRepository busqueda) {
        this.repo = repo;
        this.busqueda = busqueda;
    }

    /**
     * Resultado de una búsqueda por texto, con el motivo, para que el chatbot responda con el mensaje adecuado.
     */
    public record ResultadoBusqueda(List<ProductoChatDTO> productos, Origen origen) {
        public enum Origen {
            /** Productos que coinciden con lo que se buscó. */
            COINCIDENCIAS,
            /** No hay más coincidencias, pero sí productos de la misma subcategoría que los ya mostrados. */
            SIMILARES,
            /** El cliente pidió más opciones y ya se le mostró todo lo que hay. */
            SIN_MAS_OPCIONES,
            /** No hay nada que coincida. */
            SIN_RESULTADOS
        }
    }

    // ------------------------------------------------------------------
    // Flujo guiado
    // ------------------------------------------------------------------

    /**
     * Devuelve el siguiente paso del flujo según los filtros que el cliente ya eligió.
     * Con filtros vacíos devuelve el primer paso (las categorías).
     */
    @Transactional(readOnly = true)
    public ChatbotPasoResponse obtenerPaso(ChatbotFiltros entrada) {
        ChatbotFiltros f = normalizar(entrada);
        LocalDate hoy = LocalDate.now();
        boolean ofertas = f.isSoloOfertas();

        // 1) Categoría
        if (f.getIdCategoria() == null) {
            List<OpcionDTO> categorias = repo.opcionesCategoria(hoy, ofertas);
            if (categorias.isEmpty()) {
                return sinResultados(f);
            }
            boolean saltar = categorias.size() == 1
                    && cubreTodo(categorias.get(0), SIN_FILTRO, SIN_FILTRO, SIN_FILTRO, hoy, ofertas);
            if (!saltar) {
                return conOpciones(PasoChatbot.CATEGORIA, f, categorias, preguntaCategoria(f));
            }
            f.setIdCategoria(categorias.get(0).getId());
            f.setNombreCategoria(categorias.get(0).getNombre());
        }

        // 2) Subcategoría
        if (f.getIdSubcategoria() == null && !f.isOmitirSubcategoria()) {
            List<OpcionDTO> subcategorias = repo.opcionesSubcategoria(f.getIdCategoria(), hoy, ofertas);
            if (subcategorias.size() == 1
                    && cubreTodo(subcategorias.get(0), f.getIdCategoria(), SIN_FILTRO, SIN_FILTRO, hoy, ofertas)) {
                f.setIdSubcategoria(subcategorias.get(0).getId());
                f.setNombreSubcategoria(subcategorias.get(0).getNombre());
            } else if (!subcategorias.isEmpty()) {
                return conOpciones(PasoChatbot.SUBCATEGORIA, f, subcategorias, preguntaSubcategoria(f));
            }
            // Sin subcategorías con productos: se continúa con la marca.
        }

        // 3) Marca
        if (f.getIdMarca() == null && !f.isOmitirMarca()) {
            int idSub = idONinguno(f.getIdSubcategoria());
            List<OpcionDTO> marcas = repo.opcionesMarca(f.getIdCategoria(), idSub, hoy, ofertas);
            if (marcas.size() == 1
                    && cubreTodo(marcas.get(0), f.getIdCategoria(), idSub, SIN_FILTRO, hoy, ofertas)) {
                f.setIdMarca(marcas.get(0).getId());
                f.setNombreMarca(marcas.get(0).getNombre());
            } else if (!marcas.isEmpty()) {
                return conOpciones(PasoChatbot.MARCA, f, marcas, preguntaMarca(f));
            }
        }

        // 4) Productos
        Slice<ProductoChatDTO> productos = repo.buscarProductos(
                f.getIdCategoria(),
                idONinguno(f.getIdSubcategoria()),
                idONinguno(f.getIdMarca()),
                ofertas,
                true,
                "%",
                hoy,
                PageRequest.of(f.getPagina(), PRODUCTOS_POR_PAGINA));

        if (productos.isEmpty()) {
            return sinResultados(f);
        }

        return ChatbotPasoResponse.builder()
                .paso(PasoChatbot.PRODUCTOS)
                .pregunta(preguntaProductos(f))
                .opciones(List.of())
                .productos(productos.getContent())
                .hayMas(productos.hasNext())
                .filtros(f)
                .build();
    }

    // ------------------------------------------------------------------
    // Búsqueda por texto (la usa el chatbot cuando el cliente escribe)
    // ------------------------------------------------------------------

    /**
     * Busca productos a partir de los términos que SofIA dedujo de la conversación.
     *
     * - Coincide con el nombre, la descripción, la categoría o la subcategoría; sin importar tildes ni plurales.
     * - Respeta lo que el cliente ya eligió con los botones. Si con esos filtros no hay nada, repite la búsqueda
     *   en todo el catálogo: el cliente pudo cambiar de tema.
     * - Con "otrasOpciones" excluye lo que ya se le mostró; si no queda nada, ofrece productos de la misma
     *   subcategoría y, si tampoco los hay, avisa que ya vio todo lo que existe.
     * - Si la búsqueda avanzada fallara por cualquier motivo, usa la búsqueda simple por nombre, de modo que el
     *   chatbot nunca se quede sin buscar.
     *
     * No es @Transactional a propósito (ver ChatbotBusquedaRepository).
     */
    public ResultadoBusqueda buscarPorTexto(List<String> terminos, ChatbotFiltros filtros, boolean soloOfertas,
                                            boolean otrasOpciones, Collection<Integer> yaMostrados) {
        LocalDate hoy = LocalDate.now();
        List<String> patrones = TextoChat.patrones(terminos, MAX_PATRONES);
        List<Integer> mostrados = yaMostrados == null ? List.of()
                : yaMostrados.stream().filter(Objects::nonNull).distinct().limit(MAX_MOSTRADOS).toList();
        boolean pideMas = otrasOpciones && !mostrados.isEmpty();
        List<Integer> excluir = pideMas ? mostrados : List.of();

        // Sin términos solo tiene sentido pedir "las ofertas" en general
        if (patrones.isEmpty() && !soloOfertas) {
            return new ResultadoBusqueda(List.of(), ResultadoBusqueda.Origen.SIN_RESULTADOS);
        }

        int idCategoria = filtros == null ? SIN_FILTRO : idONinguno(filtros.getIdCategoria());
        int idSubcategoria = filtros == null ? SIN_FILTRO : idONinguno(filtros.getIdSubcategoria());
        int idMarca = filtros == null ? SIN_FILTRO : idONinguno(filtros.getIdMarca());
        boolean habiaFiltros = idCategoria != SIN_FILTRO || idSubcategoria != SIN_FILTRO || idMarca != SIN_FILTRO;
        boolean ofertas = soloOfertas || (filtros != null && filtros.isSoloOfertas());

        List<ProductoChatDTO> encontrados;
        try {
            encontrados = busqueda.buscar(patrones, idCategoria, idSubcategoria, idMarca, ofertas,
                    excluir, hoy, PRODUCTOS_POR_PAGINA);
            if (encontrados.isEmpty() && habiaFiltros) {
                encontrados = busqueda.buscar(patrones, SIN_FILTRO, SIN_FILTRO, SIN_FILTRO, soloOfertas,
                        excluir, hoy, PRODUCTOS_POR_PAGINA);
            }
            if (encontrados.isEmpty() && pideMas) {
                List<ProductoChatDTO> similares = busqueda.similares(mostrados, hoy, PRODUCTOS_POR_PAGINA);
                return similares.isEmpty()
                        ? new ResultadoBusqueda(List.of(), ResultadoBusqueda.Origen.SIN_MAS_OPCIONES)
                        : new ResultadoBusqueda(similares, ResultadoBusqueda.Origen.SIMILARES);
            }
        } catch (RuntimeException e) {
            log.error("[Chatbot] Falló la búsqueda avanzada; se usa la búsqueda simple por nombre.", e);
            encontrados = busquedaSimple(terminos, idCategoria, idSubcategoria, idMarca, ofertas, soloOfertas, hoy);
        }

        return encontrados.isEmpty()
                ? new ResultadoBusqueda(List.of(), pideMas ? ResultadoBusqueda.Origen.SIN_MAS_OPCIONES
                        : ResultadoBusqueda.Origen.SIN_RESULTADOS)
                : new ResultadoBusqueda(encontrados, ResultadoBusqueda.Origen.COINCIDENCIAS);
    }

    /** Búsqueda de respaldo: solo por nombre y descripción, con el primer término. */
    private List<ProductoChatDTO> busquedaSimple(List<String> terminos, int idCategoria, int idSubcategoria,
                                                 int idMarca, boolean ofertas, boolean soloOfertas, LocalDate hoy) {
        String primero = terminos == null || terminos.isEmpty() ? "" : terminos.get(0);
        String patron = construirPatron(primero);
        List<ProductoChatDTO> resultado = repo.buscarProductos(idCategoria, idSubcategoria, idMarca, ofertas,
                false, patron, hoy, PageRequest.of(0, PRODUCTOS_POR_PAGINA)).getContent();
        boolean habiaFiltros = idCategoria != SIN_FILTRO || idSubcategoria != SIN_FILTRO || idMarca != SIN_FILTRO;
        if (resultado.isEmpty() && habiaFiltros) {
            resultado = repo.buscarProductos(SIN_FILTRO, SIN_FILTRO, SIN_FILTRO, soloOfertas,
                    false, patron, hoy, PageRequest.of(0, PRODUCTOS_POR_PAGINA)).getContent();
        }
        return resultado;
    }

    /**
     * Copia los filtros recibidos descartando lo que no tiene sentido (por ejemplo una marca sin
     * categoría) y completando los nombres que falten. Nunca devuelve null.
     */
    @Transactional(readOnly = true)
    public ChatbotFiltros normalizar(ChatbotFiltros entrada) {
        ChatbotFiltros f = new ChatbotFiltros();
        if (entrada == null) {
            return f;
        }

        f.setSoloOfertas(entrada.isSoloOfertas());
        f.setPagina(Math.max(0, Math.min(entrada.getPagina(), MAX_PAGINA)));

        if (entrada.getIdCategoria() == null) {
            return f; // sin categoría no se aplican subcategoría ni marca
        }
        f.setIdCategoria(entrada.getIdCategoria());
        f.setNombreCategoria(nombreOConsulta(entrada.getNombreCategoria(),
                () -> repo.nombreCategoria(entrada.getIdCategoria())));

        if (entrada.getIdSubcategoria() != null) {
            f.setIdSubcategoria(entrada.getIdSubcategoria());
            f.setNombreSubcategoria(nombreOConsulta(entrada.getNombreSubcategoria(),
                    () -> repo.nombreSubcategoria(entrada.getIdSubcategoria())));
        } else {
            f.setOmitirSubcategoria(entrada.isOmitirSubcategoria());
        }

        if (entrada.getIdMarca() != null) {
            f.setIdMarca(entrada.getIdMarca());
            f.setNombreMarca(nombreOConsulta(entrada.getNombreMarca(),
                    () -> repo.nombreMarca(entrada.getIdMarca())));
        } else {
            f.setOmitirMarca(entrada.isOmitirMarca());
        }
        return f;
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private ChatbotPasoResponse conOpciones(PasoChatbot paso, ChatbotFiltros f, List<OpcionDTO> opciones, String pregunta) {
        return ChatbotPasoResponse.builder()
                .paso(paso)
                .pregunta(pregunta)
                .opciones(opciones)
                .productos(List.of())
                .hayMas(false)
                .filtros(f)
                .build();
    }

    private ChatbotPasoResponse sinResultados(ChatbotFiltros f) {
        return ChatbotPasoResponse.builder()
                .paso(PasoChatbot.SIN_RESULTADOS)
                .pregunta(elegir(
                        "Lo siento mucho 😔 por ahora no tengo productos disponibles con esa selección. "
                                + "¿Probamos con otra opción o prefieres contarme con tus palabras qué necesitas?",
                        "Ay, no encontré productos disponibles con esa selección 😔 "
                                + "¿Quieres que empecemos de nuevo o prefieres escribirme lo que buscas?"))
                .opciones(List.of())
                .productos(List.of())
                .hayMas(false)
                .filtros(f)
                .build();
    }

    /** true si la única opción disponible contiene todos los productos del nivel anterior. */
    private boolean cubreTodo(OpcionDTO unica, int idCategoria, int idSubcategoria, int idMarca,
                              LocalDate hoy, boolean ofertas) {
        long totalNivel = repo.contarDisponibles(idCategoria, idSubcategoria, idMarca, hoy, ofertas);
        return unica.getTotal() != null && unica.getTotal() == totalNivel;
    }

    private static int idONinguno(Integer id) {
        return id == null ? SIN_FILTRO : id;
    }

    private String nombreOConsulta(String enviado, Supplier<Optional<String>> consulta) {
        String limpio = limpiarNombre(enviado);
        if (limpio != null) {
            return limpio;
        }
        return consulta.get().map(this::limpiarNombre).orElse(null);
    }

    /** El nombre solo se usa para redactar mensajes: se quitan etiquetas y se limita el largo. */
    private String limpiarNombre(String nombre) {
        if (nombre == null) {
            return null;
        }
        String limpio = nombre.replaceAll("[<>\\p{Cntrl}]", " ").trim();
        if (limpio.isEmpty()) {
            return null;
        }
        return limpio.length() > MAX_LARGO_NOMBRE ? limpio.substring(0, MAX_LARGO_NOMBRE) : limpio;
    }

    /** "paracetamol 500" → "%paracetamol%500%": las palabras en orden, con cualquier texto en medio. */
    private String construirPatron(String texto) {
        String t = texto == null ? "" : texto.toLowerCase(Locale.ROOT);
        t = t.replaceAll("[%_\\\\]", " ").trim().replaceAll("\\s+", "%");
        if (t.length() > MAX_LARGO_BUSQUEDA) {
            t = t.substring(0, MAX_LARGO_BUSQUEDA);
        }
        return "%" + t + "%";
    }

    // ------------------------------------------------------------------
    // Mensajes de SofIA (varios por paso para que la charla no suene repetida)
    // ------------------------------------------------------------------

    private String preguntaCategoria(ChatbotFiltros f) {
        if (f.isSoloOfertas()) {
            return elegir(
                    "¡Qué bien! Estas son las categorías que tienen ofertas ahora mismo 🔥 ¿Cuál te interesa?",
                    "¡Buenísimo! Mira dónde hay ofertas hoy 🔥 Elige una categoría y te las muestro.");
        }
        return elegir(
                "Cuéntame, ¿qué te gustaría buscar hoy? Elige una opción y yo te guío 😊",
                "¿Qué tipo de producto necesitas? Toca una opción y te ayudo a encontrarlo 💙",
                "Con gusto te ayudo 😊 ¿Qué categoría te interesa?");
    }

    private String preguntaSubcategoria(ChatbotFiltros f) {
        String categoria = f.getNombreCategoria() != null ? f.getNombreCategoria() : "esta categoría";
        return elegir(
                "¡Excelente elección! Dentro de " + categoria + ", ¿qué necesitas exactamente?",
                "Perfecto 😊 En " + categoria + " tengo varias opciones. ¿Cuál se parece más a lo que buscas?");
    }

    private String preguntaMarca(ChatbotFiltros f) {
        return elegir(
                "¡Ya casi! ¿Tienes alguna marca de preferencia? Si no, también puedo mostrarte todas 😊",
                "Muy bien 😊 ¿Prefieres alguna marca en particular?");
    }

    private String preguntaProductos(ChatbotFiltros f) {
        if (f.getPagina() > 0) {
            return "Aquí tienes más opciones 😊";
        }
        if (f.isSoloOfertas()) {
            return "¡Buenas noticias! Estos productos están en oferta ahora mismo 🔥";
        }
        return elegir(
                "¡Mira lo que encontré para ti! 🎉 Toca «Agregar» en el que quieras.",
                "¡Listo! Estas son las opciones que tengo para ti 😊 Toca «Agregar» en la que prefieras.");
    }

    private static String elegir(String... frases) {
        return frases[ThreadLocalRandom.current().nextInt(frases.length)];
    }
}
