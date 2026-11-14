package com.example.demo.controllers;

import com.example.demo.models.Pedido;
import com.example.demo.repositories.DetallePedidoRepository;
import com.example.demo.repositories.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void listarVentasIncluyeVentasPresencialesSinCliente() {
        PedidoRepository pedidos = mock(PedidoRepository.class);
        DetallePedidoRepository detalles = mock(DetallePedidoRepository.class);
        Pedido pedido = new Pedido();
        pedido.setIdPedido(1);
        pedido.setTotal(java.math.BigDecimal.TEN);

        when(pedidos.findAllByOrderByFechaDesc()).thenReturn(List.of(pedido));
        when(detalles.findByPedido_IdPedidoIn(List.of(1))).thenReturn(List.of());

        AdminController controller = new AdminController();
        ReflectionTestUtils.setField(controller, "pedidoRepository", pedidos);
        ReflectionTestUtils.setField(controller, "detallePedidoRepository", detalles);

        List<Map<String, Object>> response =
                (List<Map<String, Object>>) controller.listarVentas().getBody();

        Map<String, Object> usuario = (Map<String, Object>) response.get(0).get("usuario");
        assertEquals("Cliente en tienda", usuario.get("nombre"));
        assertEquals("", usuario.get("apellido"));
        assertEquals("", usuario.get("email"));
    }
}
