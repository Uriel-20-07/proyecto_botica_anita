package com.example.demo.services;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private static final long VIGENCIA_ARBOL_MS = 10 * 60_000L;
    private static final long VIGENCIA_ARBOL_ERROR_MS = 60_000L;
    private static final int MAX_LARGO_NOMBRE = 80;
    private static final int MAX_LARGO_BUSQUEDA = 60;
    private static final int MAX_PAGINA = 50;

    /** Candidatos que se traen de la base de datos para luego elegir PRODUCTOS_POR_PAGINA variados. */
    private static final int CANDIDATOS = PRODUCTOS_POR_PAGINA * 5;

    private final ChatbotCatalogoRepository repo;
    private final ChatbotBusquedaRepository busqueda;

    /** Categorías donde se buscan productos para un síntoma o malestar (configurable). */
    private final List<String> categoriasSalud;

    private record ArbolEnCache(ArbolCategorias arbol, long venceEn) { }

    private volatile ArbolEnCache arbolEnCache;

    public ChatbotCatalogoService(ChatbotCatalogoRepository repo, ChatbotBusquedaRepository busqueda,
                                  @Value("${chatbot.categorias-salud:MEDICAMENTOS}") String categoriasSalud) {
        this.repo = repo;
        this.busqueda = busqueda;
        this.categoriasSalud = Arrays.stream(categoriasSalud.split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).toList();
    }

    /**
     * Lo que SofIA pidió buscar.
     *
     * @param forma forma farmacéutica que pidió el cliente ("solida", "liquida", "topica") o null.
     */
    public record PedidoBusqueda(List<String> terminos, List<String> categorias, boolean esSintoma,
                                 boolean soloOfertas, boolean otrasOpciones, String forma) {

        public PedidoBusqueda(List<String> terminos, List<String> categorias, boolean esSintoma,
                              boolean soloOfertas, boolean otrasOpciones) {
            this(terminos, categorias, esSintoma, soloOfertas, otrasOpciones, null);
        }
    }

    /**
     * Resultado de una búsqueda por texto, con el motivo, para que el chatbot responda con el mensaje adecuado.
     */
    public record ResultadoBusqueda(List<ProductoChatDTO> productos, Origen origen) {
        public enum Origen {
            /** Productos que coinciden con lo que se buscó. */
            COINCIDENCIAS,
            /** Nada coincide con las palabras, pero sí hay productos en la sección (categoría) correcta. */
            CATEGORIA,
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
     * Categorías y subcategorías de la tienda. Se guardan 10 minutos para no consultarlas en cada mensaje.
     * Si no se pueden leer devuelve un árbol vacío (y SofIA busca sin restricción de categoría).
     */
    public ArbolCategorias arbolCategorias() {
        ArbolEnCache enCache = arbolEnCache;
        long ahora = System.currentTimeMillis();
        if (enCache != null && ahora < enCache.venceEn()) {
            return enCache.arbol();
        }
        try {
            ArbolCategorias arbol = ArbolCategorias.desdeFilas(busqueda.arbol(LocalDate.now()));
            arbolEnCache = new ArbolEnCache(arbol, ahora + VIGENCIA_ARBOL_MS);
            return arbol;
        } catch (RuntimeException e) {
            log.error("[Chatbot] No se pudo leer el árbol de categorías; SofIA buscará sin restringir por categoría.", e);
            arbolEnCache = new ArbolEnCache(ArbolCategorias.vacio(), ahora + VIGENCIA_ARBOL_ERROR_MS);
            return ArbolCategorias.vacio();
        }
    }

    /**
     * Busca productos a partir de lo que SofIA dedujo de la conversación.
     *
     * - Coincide con el nombre, la descripción, la categoría o la subcategoría; sin importar tildes ni plurales.
     * - Si el pedido es por un síntoma, SOLO se busca en las categorías de salud (por defecto MEDICAMENTOS), sin
     *   importar lo que haya pedido SofIA: así un biberón "anticólico" nunca aparece como remedio de un cólico.
     * - Si con la subcategoría elegida no hay nada, se amplía a su categoría; y como último recurso se muestran
     *   productos de esa sección aunque no coincidan las palabras exactas.
     * - Respeta lo que el cliente eligió con los botones; si con eso no hay nada, repite sin esos filtros.
     * - Con "otrasOpciones" excluye lo ya mostrado; si no queda nada, ofrece productos de la misma subcategoría
     *   y, si tampoco los hay, avisa que ya vio todo lo que existe.
     * - Si la búsqueda avanzada fallara, usa la búsqueda simple por nombre (salvo en síntomas, donde no hay forma
     *   de garantizar que solo se muestren medicamentos).
     *
     * No es @Transactional a propósito (ver ChatbotBusquedaRepository).
     */
    public ResultadoBusqueda buscarPorTexto(PedidoBusqueda pedido, ChatbotFiltros filtros, Collection<Integer> yaMostrados) {
        LocalDate hoy = LocalDate.now();
        List<String> patrones = TextoChat.patrones(pedido.terminos(), MAX_PATRONES);
        List<Integer> mostrados = yaMostrados == null ? List.of()
                : yaMostrados.stream().filter(Objects::nonNull).distinct().limit(MAX_MOSTRADOS).toList();
        boolean pideMas = pedido.otrasOpciones() && !mostrados.isEmpty();
        List<Integer> excluir = pideMas ? mostrados : List.of();

        ArbolCategorias arbol = arbolCategorias();
        List<String> categorias = arbol.resolver(pedido.categorias());
        if (pedido.esSintoma()) {
            categorias = arbol.dentroDeSalud(categorias, categoriasSalud);
        }

        // Sin términos ni categorías solo tiene sentido pedir "las ofertas" en general
        if (patrones.isEmpty() && categorias.isEmpty() && !pedido.soloOfertas()) {
            return new ResultadoBusqueda(List.of(), ResultadoBusqueda.Origen.SIN_RESULTADOS);
        }

        int idCategoria = filtros == null ? SIN_FILTRO : idONinguno(filtros.getIdCategoria());
        int idSubcategoria = filtros == null ? SIN_FILTRO : idONinguno(filtros.getIdSubcategoria());
        int idMarca = filtros == null ? SIN_FILTRO : idONinguno(filtros.getIdMarca());
        boolean habiaFiltros = idCategoria != SIN_FILTRO || idSubcategoria != SIN_FILTRO || idMarca != SIN_FILTRO;
        boolean ofertas = pedido.soloOfertas() || (filtros != null && filtros.isSoloOfertas());
        SeleccionChat.Forma forma = SeleccionChat.formaPedida(pedido.forma(), pedido.terminos());

        List<ProductoChatDTO> encontrados;
        try {
            encontrados = variados(busqueda.buscar(patrones, idCategoria, idSubcategoria, idMarca, ofertas,
                    excluir, categorias, hoy, CANDIDATOS), forma);
            if (encontrados.isEmpty() && habiaFiltros) {
                encontrados = variados(busqueda.buscar(patrones, SIN_FILTRO, SIN_FILTRO, SIN_FILTRO, pedido.soloOfertas(),
                        excluir, categorias, hoy, CANDIDATOS), forma);
            }
            if (encontrados.isEmpty() && !categorias.isEmpty()) {
                List<String> padres = arbol.padresDe(categorias);
                if (!padres.isEmpty()) {
                    encontrados = variados(busqueda.buscar(patrones, SIN_FILTRO, SIN_FILTRO, SIN_FILTRO, pedido.soloOfertas(),
                            excluir, padres, hoy, CANDIDATOS), forma);
                }
            }
            if (encontrados.isEmpty() && pideMas) {
                List<ProductoChatDTO> similares = variados(busqueda.similares(mostrados, hoy, CANDIDATOS), forma);
                return similares.isEmpty()
                        ? new ResultadoBusqueda(List.of(), ResultadoBusqueda.Origen.SIN_MAS_OPCIONES)
                        : new ResultadoBusqueda(similares, ResultadoBusqueda.Origen.SIMILARES);
            }
            if (encontrados.isEmpty() && !categorias.isEmpty() && !patrones.isEmpty()) {
                List<ProductoChatDTO> deLaSeccion = variados(busqueda.buscar(List.of(), SIN_FILTRO, SIN_FILTRO, SIN_FILTRO,
                        pedido.soloOfertas(), excluir, categorias, hoy, CANDIDATOS), forma);
                if (!deLaSeccion.isEmpty()) {
                    return new ResultadoBusqueda(deLaSeccion, ResultadoBusqueda.Origen.CATEGORIA);
                }
            }
        } catch (RuntimeException e) {
            log.error("[Chatbot] Falló la búsqueda avanzada.", e);
            if (pedido.esSintoma()) {
                return new ResultadoBusqueda(List.of(), ResultadoBusqueda.Origen.SIN_RESULTADOS);
            }
            log.warn("[Chatbot] Se usa la búsqueda simple por nombre.");
            encontrados = busquedaSimple(pedido.terminos(), idCategoria, idSubcategoria, idMarca, ofertas,
                    pedido.soloOfertas(), hoy);
        }

        return encontrados.isEmpty()
                ? new ResultadoBusqueda(List.of(), pideMas ? ResultadoBusqueda.Origen.SIN_MAS_OPCIONES
                        : ResultadoBusqueda.Origen.SIN_RESULTADOS)
                : new ResultadoBusqueda(encontrados, ResultadoBusqueda.Origen.COINCIDENCIAS);
    }

    /** Elige hasta PRODUCTOS_POR_PAGINA productos variados (forma pedida, marcas distintas) de los candidatos. */
    private static List<ProductoChatDTO> variados(List<ProductoChatDTO> candidatos, SeleccionChat.Forma forma) {
        return SeleccionChat.elegir(candidatos, forma, PRODUCTOS_POR_PAGINA);
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
