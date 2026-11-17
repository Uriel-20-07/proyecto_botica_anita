package com.example.demo.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.example.demo.dto.chatbot.OpcionDTO;
import com.example.demo.dto.chatbot.ProductoChatDTO;
import com.example.demo.models.Producto;

/**
 * Consultas del chatbot sobre el catálogo.
 *
 * Todas devuelven DTOs mediante "select new ..." (proyección): la base de datos entrega únicamente las
 * columnas que el chat necesita, sin cargar entidades completas ni sus relaciones EAGER
 * (categoría → subcategorías, marca...), que era lo que volvía lenta la búsqueda anterior.
 *
 * Convención: un id de filtro con valor -1 significa "sin filtro". Se usa -1 en lugar de null porque
 * PostgreSQL no puede deducir el tipo de un parámetro null en expresiones "(:id is null or ...)".
 *
 * Extiende Repository (no JpaRepository) a propósito: expone solo lo que el chatbot necesita.
 */
public interface ChatbotCatalogoRepository extends Repository<Producto, Integer> {

    int SIN_FILTRO = -1;

    // ---- Fragmentos JPQL reutilizados ----

    /** Producto con stock y sin vencer. */
    String DISPONIBLE = "(p.stock > 0 and (p.fechaCaducidad is null or p.fechaCaducidad >= :hoy))";

    /** Oferta vigente: marcada como oferta, con precio de oferta menor al regular. */
    String EN_OFERTA = "(p.enOferta = true and p.precioOferta is not null and p.precioOferta < p.precioVenta)";

    String FILTRO_OFERTAS = "(:soloOfertas = false or " + EN_OFERTA + ")";
    String FILTRO_CATEGORIA = "(:idCategoria = -1 or p.categoria.idCategoria = :idCategoria)";
    String FILTRO_SUBCATEGORIA = "(:idSubcategoria = -1 or p.subcategoria.idSubcategoria = :idSubcategoria)";
    String FILTRO_MARCA = "(:idMarca = -1 or p.marca.idMarca = :idMarca)";

    // ---- Paso 1: categorías ----

    @Query("select new com.example.demo.dto.chatbot.OpcionDTO(c.idCategoria, c.nombre, count(p), "
            + "count(case when " + EN_OFERTA + " then 1 end)) "
            + "from Producto p join p.categoria c "
            + "where " + DISPONIBLE + " and " + FILTRO_OFERTAS + " "
            + "group by c.idCategoria, c.nombre "
            + "order by c.nombre")
    List<OpcionDTO> opcionesCategoria(@Param("hoy") LocalDate hoy,
                                      @Param("soloOfertas") boolean soloOfertas);

    // ---- Paso 2: subcategorías de una categoría ----

    @Query("select new com.example.demo.dto.chatbot.OpcionDTO(s.idSubcategoria, s.nombre, count(p), "
            + "count(case when " + EN_OFERTA + " then 1 end)) "
            + "from Producto p join p.subcategoria s "
            + "where p.categoria.idCategoria = :idCategoria and " + DISPONIBLE + " and " + FILTRO_OFERTAS + " "
            + "group by s.idSubcategoria, s.nombre, s.orden "
            + "order by s.orden, s.nombre")
    List<OpcionDTO> opcionesSubcategoria(@Param("idCategoria") int idCategoria,
                                         @Param("hoy") LocalDate hoy,
                                         @Param("soloOfertas") boolean soloOfertas);

    // ---- Paso 3: marcas dentro de la categoría / subcategoría elegida ----
    // Marca no está ligada a categoría: la relación se obtiene a través de los productos.

    @Query("select new com.example.demo.dto.chatbot.OpcionDTO(m.idMarca, m.nombre, count(p), "
            + "count(case when " + EN_OFERTA + " then 1 end)) "
            + "from Producto p join p.marca m "
            + "where " + FILTRO_CATEGORIA + " and " + FILTRO_SUBCATEGORIA + " and "
            + DISPONIBLE + " and " + FILTRO_OFERTAS + " "
            + "group by m.idMarca, m.nombre "
            + "order by m.nombre")
    List<OpcionDTO> opcionesMarca(@Param("idCategoria") int idCategoria,
                                  @Param("idSubcategoria") int idSubcategoria,
                                  @Param("hoy") LocalDate hoy,
                                  @Param("soloOfertas") boolean soloOfertas);

    /**
     * Cuenta los productos disponibles que cumplen los filtros. Se usa para saber si una opción única
     * "cubre" todo el catálogo del nivel anterior antes de saltarse ese paso (así no se esconden
     * productos que no tienen subcategoría o marca asignada).
     */
    @Query("select count(p) from Producto p "
            + "where " + FILTRO_CATEGORIA + " and " + FILTRO_SUBCATEGORIA + " and " + FILTRO_MARCA + " and "
            + DISPONIBLE + " and " + FILTRO_OFERTAS)
    long contarDisponibles(@Param("idCategoria") int idCategoria,
                           @Param("idSubcategoria") int idSubcategoria,
                           @Param("idMarca") int idMarca,
                           @Param("hoy") LocalDate hoy,
                           @Param("soloOfertas") boolean soloOfertas);

    // ---- Paso 4 y búsqueda por texto: productos ----

    /**
     * Productos que cumplen los filtros, paginados en la base de datos (Slice trae una fila extra solo
     * para saber si hay más resultados).
     *
     * Orden: disponibles primero, luego ofertas, luego coincidencia por nombre y por último alfabético.
     *
     * @param patron patrón LIKE ya en minúsculas, por ejemplo "%paracetamol%" ("%" = sin texto).
     * @param soloDisponibles true en el flujo guiado; false en la búsqueda por texto, para poder
     *                        avisar amablemente que un producto está agotado.
     */
    @Query("select new com.example.demo.dto.chatbot.ProductoChatDTO("
            + "p.idProducto, p.nombre, p.precioVenta, p.precioOferta, p.enOferta, p.stock, p.fechaCaducidad, p.imgUrl) "
            + "from Producto p "
            + "where " + FILTRO_CATEGORIA + " and " + FILTRO_SUBCATEGORIA + " and " + FILTRO_MARCA + " and "
            + FILTRO_OFERTAS + " and (:soloDisponibles = false or " + DISPONIBLE + ") and "
            + "(lower(p.nombre) like :patron or lower(p.descripcion) like :patron) "
            + "order by case when " + DISPONIBLE + " then 0 else 1 end, "
            + "case when " + EN_OFERTA + " then 0 else 1 end, "
            + "case when lower(p.nombre) like :patron then 0 else 1 end, "
            + "p.nombre")
    Slice<ProductoChatDTO> buscarProductos(@Param("idCategoria") int idCategoria,
                                           @Param("idSubcategoria") int idSubcategoria,
                                           @Param("idMarca") int idMarca,
                                           @Param("soloOfertas") boolean soloOfertas,
                                           @Param("soloDisponibles") boolean soloDisponibles,
                                           @Param("patron") String patron,
                                           @Param("hoy") LocalDate hoy,
                                           Pageable pageable);

    // ---- Nombres (solo cuando el cliente envía un id sin nombre) ----

    @Query("select c.nombre from Categoria c where c.idCategoria = :id")
    Optional<String> nombreCategoria(@Param("id") Integer id);

    @Query("select s.nombre from Subcategoria s where s.idSubcategoria = :id")
    Optional<String> nombreSubcategoria(@Param("id") Integer id);

    @Query("select m.nombre from Marca m where m.idMarca = :id")
    Optional<String> nombreMarca(@Param("id") Integer id);
}
