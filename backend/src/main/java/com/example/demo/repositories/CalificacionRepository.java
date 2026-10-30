package com.example.demo.repositories;

import com.example.demo.models.Calificacion;
import com.example.demo.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalificacionRepository extends JpaRepository<Calificacion, Integer> {

    /** ¿Ya existe una calificación para este pedido? */
    boolean existsByPedido_IdPedido(Integer idPedido);

    /** Calificaciones hechas por un cliente (para saber qué pedidos ya calificó). */
    List<Calificacion> findByUsuario(User usuario);

    /** Todas las calificaciones, de la más reciente a la más antigua (para el dashboard). */
    List<Calificacion> findAllByOrderByFechaCreacionDesc();
}
