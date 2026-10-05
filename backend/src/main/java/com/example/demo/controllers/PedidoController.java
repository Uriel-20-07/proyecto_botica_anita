package com.example.demo.controllers;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.models.DetallePedido;
import com.example.demo.models.Pedido;
import com.example.demo.models.User;
import com.example.demo.repositories.DetallePedidoRepository;
import com.example.demo.repositories.PedidoRepository;
import com.example.demo.services.AuthService;

/**
 * Controlador REST para el historial de pedidos y seguimiento del cliente.
 * 
 * Ruta base: /api/pedidos
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
     */
    @GetMapping
    public ResponseEntity<?> obtenerPedidos(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Usuario no autenticado"));
        }
        try {
            User usuario = authService.obtenerUsuarioPorEmail(principal.getName());
            List<Pedido> pedidos = pedidoRepository.findByUsuarioOrderByFechaDesc(usuario);

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

    /**
     * 🟢 Endpoint para el seguimiento (tracking) del pedido.
     * Ruta: GET /api/pedidos/{id}/tracking
     */
@GetMapping("/{id}/tracking")
    public ResponseEntity<?> obtenerTrackingPedido(@PathVariable("id") Integer id) {
        Optional<Pedido> pedidoOpt = pedidoRepository.findById(id);

        if (pedidoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Pedido no encontrado"));
        }

        Pedido pedido = pedidoOpt.get();

        // Validar si tiene un repartidor asignado en la BD
        if (pedido.getRepartidorId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "El pedido aún no tiene repartidor asignado"));
        }

        // Estructurar la respuesta de tracking con la ubicación guardada en el pedido
        Map<String, Object> response = new HashMap<>();
        response.put("idPedido", pedido.getIdPedido());
        response.put("estado", pedido.getEstado());
        response.put("direccionEnvio", pedido.getDireccionEnvio());
        response.put("distrito", pedido.getDistrito());
        response.put("latitudEntrega", pedido.getLatitudEntrega());
        response.put("longitudEntrega", pedido.getLongitudEntrega());

        // Datos del repartidor asignado y su GPS
        Map<String, Object> repartidorMap = new HashMap<>();
        repartidorMap.put("id", pedido.getRepartidorId());
        repartidorMap.put("latitud", pedido.getLatitudRepartidor());
        repartidorMap.put("longitud", pedido.getLongitudRepartidor());
        repartidorMap.put("ultimaActualizacion", pedido.getUltimaActualizacionUbicacion());

        response.put("repartidor", repartidorMap);

        return ResponseEntity.ok(response);
    }
    }