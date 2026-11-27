package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.SliceImpl;

import com.example.demo.dto.chatbot.ChatbotFiltros;
import com.example.demo.dto.chatbot.ProductoChatDTO;
import com.example.demo.repositories.ChatbotBusquedaRepository;
import com.example.demo.repositories.ChatbotCatalogoRepository;
import com.example.demo.services.ChatbotCatalogoService.PedidoBusqueda;
import com.example.demo.services.ChatbotCatalogoService.ResultadoBusqueda;
import com.example.demo.services.ChatbotCatalogoService.ResultadoBusqueda.Origen;

/**
 * Pruebas de la lógica de búsqueda por texto del chatbot, con repositorios simulados (sin base de datos).
 * Lo más importante: una búsqueda por síntoma NUNCA sale de las categorías de salud, ni siquiera si algo falla.
 */
class ChatbotCatalogoServiceBusquedaTest {

    /** Una consulta que recibió el repositorio de búsqueda. */
    private record Llamada(List<String> patrones, boolean ofertas, List<Integer> excluir, List<String> categorias) { }

    /** Repositorio de búsqueda simulado: registra las consultas y responde lo que la prueba indique. */
    private static final class BusquedaFalsa extends ChatbotBusquedaRepository {
        final List<Llamada> llamadas = new ArrayList<>();
        Function<Llamada, List<ProductoChatDTO>> respuesta = l -> List.of();
        List<ProductoChatDTO> similares = List.of();
        List<Object[]> arbol = new ArrayList<>();
        boolean fallar;
        boolean fallarArbol;
        int lecturasDelArbol;

        @Override
        public List<ProductoChatDTO> buscar(List<String> patrones, int idCategoria, int idSubcategoria, int idMarca,
                                            boolean ofertas, Collection<Integer> excluir, Collection<String> categorias,
                                            LocalDate hoy, int limite) {
            if (fallar) {
                throw new IllegalStateException("fallo simulado de la base de datos");
            }
            Llamada llamada = new Llamada(patrones, ofertas, new ArrayList<>(excluir), new ArrayList<>(categorias));
            llamadas.add(llamada);
            return respuesta.apply(llamada);
        }

        @Override
        public List<ProductoChatDTO> similares(Collection<Integer> mostrados, LocalDate hoy, int limite) {
            return similares;
        }

        @Override
        public List<Object[]> arbol(LocalDate hoy) {
            lecturasDelArbol++;
            if (fallarArbol) {
                throw new IllegalStateException("no se pudo leer el árbol");
            }
            return arbol;
        }
    }

    private final BusquedaFalsa busqueda = new BusquedaFalsa();
    private final List<ProductoChatDTO> resultadosDeLaBusquedaSimple = new ArrayList<>();
    private int consultasSimples;

    private ChatbotCatalogoService servicio() {
        ChatbotCatalogoRepository repoSimple = (ChatbotCatalogoRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {ChatbotCatalogoRepository.class},
                (proxy, metodo, args) -> {
                    if (metodo.getName().equals("buscarProductos")) {
                        consultasSimples++;
                        return new SliceImpl<>(resultadosDeLaBusquedaSimple);
                    }
                    return null;
                });
        // El catálogo real de BoticaAnita (resumido)
        for (String[] f : new String[][] {
                {"BEBÉ", "Biberones y accesorios"}, {"BEBÉ", "Pañales y toallitas"},
                {"MEDICAMENTOS", "Digestivo"}, {"MEDICAMENTOS", "Dolor, fiebre e inflamación"},
                {"MEDICAMENTOS", "Gripe, tos y garganta"}}) {
            busqueda.arbol.add(new Object[] {f[0], f[1]});
        }
        return new ChatbotCatalogoService(repoSimple, busqueda, "MEDICAMENTOS");
    }

    private static ProductoChatDTO producto(int id, String nombre) {
        return new ProductoChatDTO(id, nombre, new BigDecimal("10.00"), null, false, 5, null, null);
    }

    private static PedidoBusqueda pedido(List<String> terminos, List<String> categorias, boolean sintoma,
                                         boolean ofertas, boolean otras) {
        return new PedidoBusqueda(terminos, categorias, sintoma, ofertas, otras);
    }

    // ---------- el candado de salud ----------

    @Test
    void unaBusquedaPorSintomaSoloMiraEnMedicamentosAunqueSofiaPidaBebe() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.respuesta = l -> List.of(producto(1, "Buscapina"));

        // El caso real: "cólico" y SofIA pidió también la sección de biberones
        ResultadoBusqueda r = servicio.buscarPorTexto(
                pedido(List.of("cólico"), List.of("Digestivo", "Biberones y accesorios"), true, false, false),
                new ChatbotFiltros(), List.of());

        assertEquals(Origen.COINCIDENCIAS, r.origen());
        assertEquals(List.of("digestivo"), busqueda.llamadas.get(0).categorias());
    }

    @Test
    void sinCategoriasPedidasUnSintomaBuscaEnTodaLaCategoriaDeSalud() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.respuesta = l -> List.of(producto(1, "Buscapina"));

        servicio.buscarPorTexto(pedido(List.of("dolor de estómago"), List.of(), true, false, false),
                new ChatbotFiltros(), List.of());

        assertEquals(List.of("medicamentos"), busqueda.llamadas.get(0).categorias());
    }

    @Test
    void siSofiaPideSoloBebeParaUnSintomaSeUsaMedicamentos() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.respuesta = l -> List.of(producto(1, "Buscapina"));

        servicio.buscarPorTexto(pedido(List.of("colico"), List.of("BEBÉ"), true, false, false),
                new ChatbotFiltros(), List.of());

        assertEquals(List.of("medicamentos"), busqueda.llamadas.get(0).categorias());
    }

    @Test
    void siNoEsSintomaSeRespetaLaCategoriaPedida() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.respuesta = l -> List.of(producto(9, "Biberón Avent"));

        // Un cliente que pide biberones: se busca en BEBÉ, sin restricciones médicas
        servicio.buscarPorTexto(pedido(List.of(), List.of("Biberones y accesorios"), false, false, false),
                new ChatbotFiltros(), List.of());

        assertEquals(List.of("biberones y accesorios"), busqueda.llamadas.get(0).categorias());
        assertTrue(busqueda.llamadas.get(0).patrones().isEmpty());
    }

    // ---------- la escalera de respaldo ----------

    @Test
    void siLaSubcategoriaNoTieneNadaSeAmpliaASuCategoria() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.respuesta = l -> l.categorias().equals(List.of("medicamentos")) ? List.of(producto(2, "Gaseovet")) : List.of();

        ResultadoBusqueda r = servicio.buscarPorTexto(
                pedido(List.of("cólico"), List.of("Digestivo"), true, false, false), new ChatbotFiltros(), List.of());

        assertEquals(Origen.COINCIDENCIAS, r.origen());
        assertEquals(List.of("digestivo"), busqueda.llamadas.get(0).categorias());
        assertEquals(List.of("medicamentos"), busqueda.llamadas.get(1).categorias());
    }

    @Test
    void comoUltimoRecursoSeMuestranProductosDeLaSeccionAunqueNoCoincidanLasPalabras() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.respuesta = l -> l.patrones().isEmpty() ? List.of(producto(3, "Omeprazol")) : List.of();

        ResultadoBusqueda r = servicio.buscarPorTexto(
                pedido(List.of("palabra rara"), List.of("Digestivo"), true, false, false), new ChatbotFiltros(), List.of());

        assertEquals(Origen.CATEGORIA, r.origen());
        assertEquals(1, r.productos().size());
        // La última consulta fue sin palabras y sigue limitada a la sección correcta
        Llamada ultima = busqueda.llamadas.get(busqueda.llamadas.size() - 1);
        assertTrue(ultima.patrones().isEmpty());
        assertEquals(List.of("digestivo"), ultima.categorias());
    }

    @Test
    void sinNadaQueMostrarDevuelveSinResultados() {
        ChatbotCatalogoService servicio = servicio();

        ResultadoBusqueda r = servicio.buscarPorTexto(
                pedido(List.of("zzz"), List.of("Digestivo"), true, false, false), new ChatbotFiltros(), List.of());

        assertEquals(Origen.SIN_RESULTADOS, r.origen());
        assertTrue(r.productos().isEmpty());
    }

    // ---------- otras opciones ----------

    @Test
    void conOtrasOpcionesSeExcluyeLoYaMostrado() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.respuesta = l -> List.of(producto(4, "Otro producto"));

        servicio.buscarPorTexto(pedido(List.of("analgésico"), List.of("Dolor, fiebre e inflamación"), true, false, true),
                new ChatbotFiltros(), List.of(1, 2, 3));

        assertEquals(List.of(1, 2, 3), busqueda.llamadas.get(0).excluir());
    }

    @Test
    void sinOtrasOpcionesNoSeExcluyeNada() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.respuesta = l -> List.of(producto(4, "Producto"));

        servicio.buscarPorTexto(pedido(List.of("analgésico"), List.of(), true, false, false),
                new ChatbotFiltros(), List.of(1, 2, 3));

        assertEquals(List.of(), busqueda.llamadas.get(0).excluir());
    }

    @Test
    void siYaNoHayMasCoincidenciasSeOfrecenSimilaresDeLaMismaSubcategoria() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.similares = List.of(producto(5, "Similar"));

        ResultadoBusqueda r = servicio.buscarPorTexto(
                pedido(List.of("analgésico"), List.of("Dolor, fiebre e inflamación"), true, false, true),
                new ChatbotFiltros(), List.of(1, 2, 3));

        assertEquals(Origen.SIMILARES, r.origen());
        assertEquals(5, r.productos().get(0).getIdProducto());
    }

    @Test
    void siYaSeMostroTodoSeAvisaQueNoHayMas() {
        ChatbotCatalogoService servicio = servicio();

        ResultadoBusqueda r = servicio.buscarPorTexto(
                pedido(List.of("analgésico"), List.of("Dolor, fiebre e inflamación"), true, false, true),
                new ChatbotFiltros(), List.of(1, 2, 3));

        assertEquals(Origen.SIN_MAS_OPCIONES, r.origen());
    }

    // ---------- cuando algo falla ----------

    @Test
    void siFallaLaBusquedaAvanzadaUnSintomaNuncaUsaLaBusquedaSimple() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.fallar = true;
        resultadosDeLaBusquedaSimple.add(producto(9, "Biberón Avent")); // la simple no distingue categorías

        ResultadoBusqueda r = servicio.buscarPorTexto(
                pedido(List.of("cólico"), List.of("Digestivo"), true, false, false), new ChatbotFiltros(), List.of());

        assertEquals(Origen.SIN_RESULTADOS, r.origen());
        assertTrue(r.productos().isEmpty());
        assertEquals(0, consultasSimples);
    }

    @Test
    void siFallaLaBusquedaAvanzadaUnProductoNormalUsaLaBusquedaSimple() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.fallar = true;
        resultadosDeLaBusquedaSimple.add(producto(7, "Paracetamol 500mg"));

        ResultadoBusqueda r = servicio.buscarPorTexto(
                pedido(List.of("paracetamol"), List.of(), false, false, false), new ChatbotFiltros(), List.of());

        assertEquals(Origen.COINCIDENCIAS, r.origen());
        assertEquals(1, consultasSimples);
    }

    @Test
    void siNoSePuedeLeerElArbolSeBuscaSinRestringirYSeIntentaDeNuevoMasTarde() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.fallarArbol = true;
        busqueda.respuesta = l -> List.of(producto(1, "Algo"));

        servicio.buscarPorTexto(pedido(List.of("cólico"), List.of("Digestivo"), true, false, false),
                new ChatbotFiltros(), List.of());

        assertEquals(List.of(), busqueda.llamadas.get(0).categorias());
        // El error se recuerda un minuto: no se vuelve a consultar en cada mensaje
        servicio.buscarPorTexto(pedido(List.of("cólico"), List.of(), true, false, false), new ChatbotFiltros(), List.of());
        assertEquals(1, busqueda.lecturasDelArbol);
    }

    // ---------- casos límite ----------

    @Test
    void sinTerminosNiCategoriasNoConsultaLaBaseDeDatos() {
        ChatbotCatalogoService servicio = servicio();

        ResultadoBusqueda r = servicio.buscarPorTexto(pedido(List.of(), List.of(), false, false, false),
                new ChatbotFiltros(), List.of());

        assertEquals(Origen.SIN_RESULTADOS, r.origen());
        assertEquals(0, busqueda.llamadas.size());
    }

    @Test
    void lasOfertasEnGeneralSePuedenPedirSinTerminos() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.respuesta = l -> List.of(producto(1, "En oferta"));

        ResultadoBusqueda r = servicio.buscarPorTexto(pedido(List.of(), List.of(), false, true, false),
                new ChatbotFiltros(), List.of());

        assertEquals(Origen.COINCIDENCIAS, r.origen());
        assertTrue(busqueda.llamadas.get(0).ofertas());
    }

    @Test
    void elArbolSeGuardaEnMemoriaEntreMensajes() {
        ChatbotCatalogoService servicio = servicio();
        busqueda.respuesta = l -> List.of(producto(1, "Algo"));

        for (int i = 0; i < 3; i++) {
            servicio.buscarPorTexto(pedido(List.of("tos"), List.of("Gripe, tos y garganta"), true, false, false),
                    new ChatbotFiltros(), List.of());
        }
        assertEquals(1, busqueda.lecturasDelArbol);
    }
}
