package com.example.demo.repositories;

import com.example.demo.models.Favorito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FavoritoRepository extends JpaRepository<Favorito, Integer> {
    List<Favorito> findByUsuarioId(Integer idUsuario);
    Optional<Favorito> findByUsuarioIdAndProductoIdProducto(Integer idUsuario, Integer idProducto);
    boolean existsByUsuarioIdAndProductoIdProducto(Integer idUsuario, Integer idProducto);
}
