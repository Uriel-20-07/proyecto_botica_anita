package com.example.demo.repositories;

import com.example.demo.models.Pedido;
import com.example.demo.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Integer> {
    List<Pedido> findByUsuarioOrderByFechaDesc(User usuario);
    List<Pedido> findAllByOrderByFechaDesc();
    long countByUsuario(User usuario);
    List<Pedido> findByIdReceta(Integer idReceta);
}
