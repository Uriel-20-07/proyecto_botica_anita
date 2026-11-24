package com.example.demo.repositories;

import com.example.demo.models.Producto;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {
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
}
