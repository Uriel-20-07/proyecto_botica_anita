package com.example.demo.repositories;

import com.example.demo.dto.ConteoDTO;
import com.example.demo.models.Producto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer>, JpaSpecificationExecutor<Producto> {
    // Buscar por nombre (ignora mayúsculas/minúsculas)
    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    // Filtrar por categoría
    List<Producto> findByCategoria_IdCategoria(Integer idCategoria);

    /**
     * Carga el producto bloqueando la fila hasta el final de la transacción.
     * Se usa para reservar/liberar stock sin que dos usuarios se pisen.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.idProducto = :id")
    Optional<Producto> findByIdForUpdate(@Param("id") Integer id);

    // ─── Conteos reales para el menú lateral del catálogo ───────────────────

    /**
     * Cuántos productos tiene cada subcategoría (las subcategorías sin
     * productos no aparecen; el frontend muestra 0 en ese caso).
     */
    @Query("select new com.example.demo.dto.ConteoDTO(s.idSubcategoria, s.nombre, count(p)) "
            + "from Producto p join p.subcategoria s "
            + "group by s.idSubcategoria, s.nombre")
    List<ConteoDTO> contarPorSubcategoria();

    /**
     * Cuántos productos tiene cada marca en todo el catálogo.
     */
    @Query("select new com.example.demo.dto.ConteoDTO(m.idMarca, m.nombre, count(p)) "
            + "from Producto p join p.marca m "
            + "group by m.idMarca, m.nombre")
    List<ConteoDTO> contarPorMarca();

    /**
     * Cuántos productos tiene cada marca dentro de una categoría.
     */
    @Query("select new com.example.demo.dto.ConteoDTO(m.idMarca, m.nombre, count(p)) "
            + "from Producto p join p.marca m "
            + "where p.categoria.idCategoria = :categoriaId "
            + "group by m.idMarca, m.nombre")
    List<ConteoDTO> contarPorMarcaEnCategoria(@Param("categoriaId") Integer categoriaId);

    /**
     * Precio más alto del catálogo (toma el precio de oferta si existe),
     * para el tope del slider de precio del frontend.
     */
    @Query("select max(p.precioConDescuento) from Producto p")
    BigDecimal findPrecioMaximoGlobal();

    // La paginación del catálogo NO vive en un @Query fijo: se arma con
    // Specifications (JpaSpecificationExecutor) en ProductoService, porque
    // los filtros de categoría, marca, subcategoría, búsqueda, ofertas y
    // precio son todos opcionales. Esa Specification además hace
    // root.fetch(...) de marca/categoría/subcategoría para resolver el N+1.
    // (Page y Pageable siguen siendo los del método findAll(spec, pageable).)
}
