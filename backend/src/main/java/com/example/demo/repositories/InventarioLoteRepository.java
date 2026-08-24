package com.example.demo.repositories;

import com.example.demo.models.InventarioLote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InventarioLoteRepository extends JpaRepository<InventarioLote, Integer> {
    // Fíjate que aquí usamos Integer idProducto
    List<InventarioLote> findByProductoIdProductoOrderByFechaVencimientoAsc(Integer idProducto);
}
