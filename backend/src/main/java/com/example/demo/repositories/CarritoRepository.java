package com.example.demo.repositories;

import com.example.demo.models.Carrito;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CarritoRepository extends JpaRepository<Carrito, Integer> {
    Optional<Carrito> findByUsuario_Id(Integer idUsuario);

    /**
     * Bloquea el carrito del usuario hasta el final de la transacción.
     * Evita que dos pestañas/reservas liberen el mismo stock a la vez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Carrito c where c.usuario.id = :idUsuario")
    Optional<Carrito> findByUsuarioIdForUpdate(@Param("idUsuario") Integer idUsuario);

    /** Bloquea un carrito por su ID (lo usa el scheduler de reservas). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Carrito c where c.idCarrito = :idCarrito")
    Optional<Carrito> findByIdForUpdate(@Param("idCarrito") Integer idCarrito);
}
