package com.example.demo.repositories;

import com.example.demo.models.Pedido;
import com.example.demo.models.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Integer> {
    List<Pedido> findByUsuarioOrderByFechaDesc(User usuario);
    List<Pedido> findAllByOrderByFechaDesc();
    long countByUsuario(User usuario);
    List<Pedido> findByIdReceta(Integer idReceta);
    boolean existsByNumeroOperacion(String numeroOperacion);

    /**
     * Historial del cliente con pedidos, detalles y productos cargados en una
     * sola consulta (evita el N+1 de consultar los detalles pedido por pedido).
     *
     * Se mantiene separado de findByUsuarioOrderByFechaDesc para que otros
     * consumidores (por ejemplo CalificacionService) no paguen el costo de
     * traer los detalles cuando no los necesitan.
     */
    @EntityGraph(attributePaths = { "detalles", "detalles.producto" })
    @Query("select distinct p from Pedido p where p.usuario = :usuario order by p.fecha desc")
    List<Pedido> findHistorialByUsuario(@Param("usuario") User usuario);
}
