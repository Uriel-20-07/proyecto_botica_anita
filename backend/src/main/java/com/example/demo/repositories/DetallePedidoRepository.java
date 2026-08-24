package com.example.demo.repositories;

import com.example.demo.models.DetallePedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Integer> {
    List<DetallePedido> findByPedido_IdPedido(Integer idPedido);
    List<DetallePedido> findByPedido_IdPedidoIn(Collection<Integer> idPedidos);
}
