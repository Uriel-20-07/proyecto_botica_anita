package com.example.demo.controllers;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.models.DetallePedido;
import com.example.demo.models.Pedido;
import com.example.demo.models.User;
import com.example.demo.repositories.DetallePedidoRepository;
import com.example.demo.repositories.PedidoRepository;
import com.example.demo.services.AuthService;

/**
 * Controlador REST para el historial de pedidos del usuario cliente.
 * 
 * Ruta base: /api/pedidos
 * CORS: permite peticiones desde el frontend Angular (localhost:4200).
 * 
 * Todos los endpoints requieren que el usuario esté autenticado.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private DetallePedidoRepository detallePedidoRepository;

    @Autowired
    private AuthService authService;

    /**
     * Obtiene el historial de pedidos del usuario autenticado.
     * 
     * @param principal usuario autenticado inyectado por Spring Security (extraído del token JWT).
     * @return 200 con la lista de pedidos y sus respectivos detalles,
     *         401 si no está autenticado, o 400 en caso de error.
     */
    @GetMapping
    public ResponseEntity<?> obtenerPedidos(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuario no autenticado"));
        }
        try {
            // Obtener el usuario cliente desde el email almacenado en el JWT (principal.getName())
            User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
            
            // Consultar pedidos ordenados por fecha descendente (más nuevos primero)
            List<Pedido> pedidos = pedidoRepository.findByUsuarioOrderByFechaDesc(usuario);

            // Mapear cada pedido a un objeto JSON detallado con sus ítems comprados
            List<Map<String, Object>> response = pedidos.stream().map(pedido -> {
                Map<String, Object> map = new HashMap<>();
                map.put("idPedido", pedido.getIdPedido());
                map.put("fecha", pedido.getFecha());
                map.put("estado", pedido.getEstado());
                map.put("total", pedido.getTotal());
                map.put("direccionEnvio", pedido.getDireccionEnvio());
                map.put("distrito", pedido.getDistrito());
                map.put("metodoPago", pedido.getMetodoPago());
                map.put("esUrgente", pedido.isEsUrgente());

                // Obtener detalles del pedido desde la BD de forma eficiente
                List<DetallePedido> detalles = detallePedidoRepository.findByPedido_IdPedido(pedido.getIdPedido());

                List<Map<String, Object>> detallesMap = detalles.stream().map(d -> {
                    Map<String, Object> dm = new HashMap<>();
                    dm.put("idDetallePedido", d.getIdDetallePedido());
                    dm.put("producto", Map.of(
                        "idProducto", d.getProducto().getIdProducto(),
                        "nombre", d.getProducto().getNombre(),
                        "precioVenta", d.getProducto().getPrecioVenta(),
                        "imgUrl", d.getProducto().getImgUrl() != null ? d.getProducto().getImgUrl() : ""
                    ));
                    dm.put("cantidad", d.getCantidad());
                    dm.put("precioHistorico", d.getPrecioHistorico());
                    return dm;
                }).collect(Collectors.toList());

                map.put("detalles", detallesMap);
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
