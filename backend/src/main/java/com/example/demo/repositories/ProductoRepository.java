package com.example.demo.repositories;

import com.example.demo.models.Producto;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {
    // Buscar por nombre (ignora mayúsculas/minúsculas)
    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    // Filtrar por categoría
    List<Producto> findByCategoria_IdCategoria(Integer idCategoria);
}

