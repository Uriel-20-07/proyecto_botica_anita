package com.example.demo.repositories;

import com.example.demo.models.Producto;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    /**
     * Todos los productos en UNA sola consulta, trayendo juntas categoría, subcategoría y marca.
     * Con findAll() cada relación se carga con consultas aparte (una por cada categoría/marca distinta),
     * y con la base de datos en la nube eso suma mucha espera. Lo usa el módulo de promociones.
     */
    @Query("select p from Producto p left join fetch p.categoria left join fetch p.subcategoria left join fetch p.marca")
    List<Producto> findAllConRelaciones();

    // Buscar por nombre (ignora mayúsculas/minúsculas)
    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    // Filtrar por categoría
    List<Producto> findByCategoria_IdCategoria(Integer idCategoria);
}

