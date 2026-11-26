package com.example.demo.repositories;

import com.example.demo.models.DetalleCarrito;
import com.example.demo.models.EstadoDetalleCarrito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DetalleCarritoRepository extends JpaRepository<DetalleCarrito, Integer> {

    List<DetalleCarrito> findByCarrito_IdCarrito(Integer idCarrito);

    List<DetalleCarrito> findByEstadoAndFechaExpiracionBefore(
            EstadoDetalleCarrito estado, LocalDateTime fecha);

    /**
     * Marca la línea como EXPIRADA solo si sigue ACTIVA y su reserva ya venció.
     * Devuelve 1 si esta ejecución fue la que la expiró (así se libera el stock una sola vez).
     */
    @Modifying
    @Query("update DetalleCarrito d set d.estado = :expirada, d.fechaExpiracion = :ahora "
            + "where d.idDetalleCarrito = :id and d.estado = :activa "
            + "and d.fechaExpiracion <= :ahora")
    int marcarExpiradaSiActiva(@Param("id") Integer id,
                               @Param("ahora") LocalDateTime ahora,
                               @Param("expirada") EstadoDetalleCarrito expirada,
                               @Param("activa") EstadoDetalleCarrito activa);
}
