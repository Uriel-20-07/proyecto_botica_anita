package com.example.demo.repositories;

import com.example.demo.models.DetalleCarrito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetalleCarritoRepository extends JpaRepository<DetalleCarrito, Integer> {
}
