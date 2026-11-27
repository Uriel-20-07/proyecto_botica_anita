package com.example.demo.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.models.PresentacionProducto;

public interface PresentacionProductoRepository extends JpaRepository<PresentacionProducto, Integer> {

    /** Presentaciones activas de un producto: la más pequeña primero (campo "orden"). */
    List<PresentacionProducto> findByProductoIdProductoAndActivoTrueOrderByOrdenAscIdPresentacionAsc(Integer idProducto);
}
