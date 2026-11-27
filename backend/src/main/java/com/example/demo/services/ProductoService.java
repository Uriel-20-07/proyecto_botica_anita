package com.example.demo.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.example.demo.dto.ConteoDTO;
import com.example.demo.dto.ConteosDTO;
import com.example.demo.dto.FiltrosCatalogo;
import com.example.demo.dto.VarianteDTO;
import com.example.demo.models.Producto;
import com.example.demo.repositories.ProductoRepository;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Servicio de Productos actualizado para sincronizar con Algolia.
 *
 * Cambios respecto al original:
 * - Se inyecta AlgoliaService.
 * - Al guardar o eliminar un producto, se actualiza Algolia automáticamente.
 * - @PostConstruct indexarTodosEnAlgolia() llena el índice al arrancar el servidor.
 */
@Service
public class ProductoService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private AlgoliaService algoliaService;

    // ─── Métodos originales (sin cambios) ────────────────────────────────────

    public List<Producto> listarTodos() {
        // Solo los que dan la cara: de una línea con tallas aparece una sola tarjeta (ver Producto.esRepresentante).
        return productoRepository.findByEsRepresentanteTrue();
    }

    /**
     * Devuelve una página del catálogo (30 productos por defecto) ya filtrada
     * en la BASE DE DATOS: primero PostgreSQL descarta lo que no coincide con
     * los filtros (sobre los 400+ productos) y recién entonces pagina de 30 en 30.
     *
     * Ordena por ID (o por el orden elegido, siempre con ID de desempate) para
     * que la paginación sea estable entre requests: sin orden determinista la BD
     * puede devolver las filas distinto cada vez y se repiten o se saltan
     * productos entre páginas.
     *
     * @param filtros filtros opcionales (categoría, subcategorías, marcas,
     *                búsqueda, ofertas, precio máximo y orden).
     * @param page    índice de página, base 0.
     * @param size    productos por página (máx. 100).
     * @return página de productos con marca, categoría y subcategoría ya cargadas.
     */
    public Page<Producto> listarPaginado(FiltrosCatalogo filtros, int page, int size) {
        int pagina = Math.max(0, page);
        int tamano = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(pagina, tamano, construirSort(filtros.orden()));
        return productoRepository.findAll(construirSpec(filtros), pageable);
    }

    /**
     * Conteos reales del catálogo para el menú lateral: productos por
     * subcategoría, productos por marca (de la categoría elegida, si hay)
     * y el precio más alto del catálogo.
     *
     * Se calcula con GROUP BY en la BD, no sobre la página cargada en memoria,
     * para que los contadores no salgan en 0 ni dependan de la página actual.
     *
     * @param categoriaId categoría opcional para acotar los conteos de marcas.
     * @return conteos por subcategoría y marca + precio máximo global.
     */
    public ConteosDTO contarProductos(Integer categoriaId) {
        List<ConteoDTO> subcategorias = productoRepository.contarPorSubcategoria();
        List<ConteoDTO> marcas = (categoriaId == null)
                ? productoRepository.contarPorMarca()
                : productoRepository.contarPorMarcaEnCategoria(categoriaId);
        BigDecimal precioMaximo = productoRepository.findPrecioMaximoGlobal();
        return new ConteosDTO(subcategorias, marcas, precioMaximo);
    }

    // ─── Detalle y tallas de una línea ───────────────────────────────────────

    /** Un producto por ID (cualquier talla, haya o no dado la cara en el catálogo). */
    public java.util.Optional<Producto> buscarPorId(Integer id) {
        return productoRepository.findById(id);
    }

    /**
     * Tallas de la línea a la que pertenece el producto, de menor a mayor. Lista vacía si el producto no tiene
     * variantes (o no existe), y el detalle se muestra como siempre.
     */
    public List<VarianteDTO> variantesDe(Integer idProducto) {
        Producto actual = productoRepository.findById(idProducto).orElse(null);
        if (actual == null || actual.getGrupoVariantes() == null || actual.getGrupoVariantes().isBlank()) {
            return List.of();
        }
        return productoRepository
                .findByGrupoVariantesOrderByVarianteOrdenAscIdProductoAsc(actual.getGrupoVariantes())
                .stream()
                .map(p -> new VarianteDTO(
                        p.getIdProducto(),
                        p.getVarianteEtiqueta() != null ? p.getVarianteEtiqueta() : p.getNombre(),
                        p.getNombre(),
                        p.getPrecioConDescuento() != null ? p.getPrecioConDescuento() : p.getPrecioVenta(),
                        p.getPrecioVenta(),
                        !p.isAgotadoOnline(),
                        p.getImgUrl()))
                .toList();
    }

    // ─── Specifications (filtros dinámicos) ──────────────────────────────────

    /**
     * Arma la Specification con los filtros que vengan activos.
     *
     * Los filtros que navegan asociaciones (categoría, subcategoría, marca) se
     * escriben con root.get(...) y funcionan igual en la consulta de datos y en
     * la de conteo, así que el total de la paginación siempre coincide con lo
     * que se muestra.
     */
    private Specification<Producto> construirSpec(FiltrosCatalogo filtros) {
        return (Root<Producto> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {

            // Evita el N+1: sin el fetch, Hibernate haría un SELECT a "marcas",
            // "categorias" y "subcategorías" por cada producto de la página.
            // En la consulta de conteo (resultType Long) el fetch no está permitido.
            if (query.getResultType() != Long.class) {
                root.fetch("marca", JoinType.LEFT);
                root.fetch("categoria", JoinType.LEFT);
                root.fetch("subcategoria", JoinType.LEFT);
            }

            List<Predicate> preds = new ArrayList<>();

            // De una línea con tallas solo aparece su representante; las demás tallas se eligen en el detalle.
            preds.add(cb.isTrue(root.get("esRepresentante")));

            if (filtros.categoriaId() != null) {
                preds.add(cb.equal(root.get("categoria").get("idCategoria"), filtros.categoriaId()));
            }

            if (filtros.subcategoriaIds() != null && !filtros.subcategoriaIds().isEmpty()) {
                preds.add(root.get("subcategoria").get("idSubcategoria").in(filtros.subcategoriaIds()));
            }

            if (filtros.marcaIds() != null && !filtros.marcaIds().isEmpty()) {
                preds.add(root.get("marca").get("idMarca").in(filtros.marcaIds()));
            }

            if (filtros.search() != null && !filtros.search().isBlank()) {
                String like = "%" + filtros.search().trim().toLowerCase() + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), like),
                        cb.like(cb.lower(root.get("descripcion")), like),
                        cb.like(cb.lower(root.get("categoria").get("nombre")), like)));
            }

            // Solo en oferta vigente: en_oferta + precio_oferta menor al precio
            // normal (descuento real) y sin fecha de caducidad vencida.
            if (Boolean.TRUE.equals(filtros.enOferta())) {
                preds.add(cb.isTrue(root.get("enOferta")));
                preds.add(cb.isNotNull(root.get("precioOferta")));
                preds.add(cb.lessThan(root.get("precioOferta"), root.get("precioVenta")));
                preds.add(cb.or(
                        cb.isNull(root.get("fechaCaducidad")),
                        cb.greaterThanOrEqualTo(root.get("fechaCaducidad"), java.time.LocalDate.now())));
            }

            // Precio efectivo (el de oferta si hay, si no el normal) hasta el tope.
            if (filtros.precioMax() != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("precioConDescuento"), filtros.precioMax()));
            }

            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }

    /** Orden pedido por el usuario + ID como desempate (paginación estable). */
    private Sort construirSort(String orden) {
        Sort base;
        boolean yaEsPorId = true;
        if ("precio_asc".equals(orden)) {
            base = Sort.by(Sort.Direction.ASC, "precioConDescuento");
            yaEsPorId = false;
        } else if ("precio_desc".equals(orden)) {
            base = Sort.by(Sort.Direction.DESC, "precioConDescuento");
            yaEsPorId = false;
        } else if ("nombre_asc".equals(orden)) {
            base = Sort.by(Sort.Direction.ASC, "nombre");
            yaEsPorId = false;
        } else {
            // "relevancia" (por defecto): orden natural por ID.
            base = Sort.by(Sort.Direction.ASC, "idProducto");
        }
        return yaEsPorId ? base : base.and(Sort.by(Sort.Direction.ASC, "idProducto"));
    }

    public List<Producto> buscarPorNombre(String nombre) {
        return productoRepository.findByNombreContainingIgnoreCase(nombre);
    }

    public List<Producto> filtrarPorCategoria(Integer idCategoria) {
        return productoRepository.findByCategoria_IdCategoria(idCategoria);
    }

    // ─── Métodos nuevos con sincronización a Algolia ──────────────────────────

    /**
     * Guarda o actualiza un producto en la BD y lo indexa en Algolia.
     *
     * @param producto producto a guardar.
     * @return el producto guardado (con ID generado si es nuevo).
     */
    public Producto guardar(Producto producto) {
        Producto guardado = productoRepository.save(producto);
        if (Boolean.FALSE.equals(guardado.getEsRepresentante())) {
            algoliaService.eliminarProducto(guardado.getIdProducto()); // las tallas no se buscan por separado
        } else {
            algoliaService.indexarProducto(guardado); // Sincronizar con Algolia
        }
        return guardado;
    }

    /**
     * Elimina un producto por ID de la BD y lo borra del índice de Algolia.
     *
     * @param id ID del producto a eliminar.
     */
    public void eliminar(Integer id) {
        productoRepository.deleteById(id);
        algoliaService.eliminarProducto(id); // Eliminar de Algolia
    }

    /**
     * Al arrancar el servidor, indexa TODOS los productos existentes en Algolia.
     * Esto asegura que el índice esté siempre sincronizado con la BD.
     *
     * Si el índice ya tiene datos, Algolia los sobreescribe (updateObject).
     * Ejecuta una sola vez por inicio de aplicación.
     */
    @PostConstruct
    public void indexarTodosEnAlgolia() {
        try {
            List<Producto> todos = productoRepository.findAll();
            // En el buscador entra una sola sugerencia por línea (su representante), no una por talla.
            List<Producto> visibles = todos.stream()
                    .filter(p -> !Boolean.FALSE.equals(p.getEsRepresentante()))
                    .toList();
            if (!visibles.isEmpty()) {
                algoliaService.indexarTodos(visibles);
                System.out.println("[Algolia] Indexación inicial: " + visibles.size() + " productos enviados.");
            }
            // Las tallas que no dan la cara se quitan del índice por si se habían indexado antes.
            todos.stream()
                    .filter(p -> Boolean.FALSE.equals(p.getEsRepresentante()))
                    .forEach(p -> algoliaService.eliminarProducto(p.getIdProducto()));
        } catch (Exception e) {
            System.err.println("[Algolia] Error en indexación inicial: " + e.getMessage());
        }
    }
}
