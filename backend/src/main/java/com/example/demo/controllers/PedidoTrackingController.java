package com.example.demo.controllers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.UbicacionDTO;
import com.example.demo.models.Pedido;
import com.example.demo.repositories.PedidoRepository;
import com.example.demo.services.PedidoTrackingService;

@RestController
@RequestMapping("/api/pedidos-tracking")
// CORS: gobernado por la configuración global en SecurityConfig (incluye
// localhost y el dominio de producción). No se restringe aquí para evitar
// que este controlador quede bloqueado en producción.
public class PedidoTrackingController {

    private final PedidoRepository pedidoRepository;
    private final PedidoTrackingService trackingService;

    public PedidoTrackingController(PedidoRepository pedidoRepository, PedidoTrackingService trackingService) {
        this.pedidoRepository = pedidoRepository;
        this.trackingService = trackingService;
    }

    /**
     * Consultar el estado y ubicación del pedido en tiempo real para el cliente.
     * Avanza el estado según el tiempo transcurrido (2 min + viaje por distancia).
     */
    @GetMapping("/{id}/tracking")
    public ResponseEntity<?> obtenerTracking(@PathVariable Integer id) {
        return pedidoRepository.findById(id)
                .<ResponseEntity<?>>map(pedido -> {
                    Pedido actualizado = trackingService.avanzarEstado(pedido);
                    Map<String, Object> response = new HashMap<>();
                    response.put("idPedido", actualizado.getIdPedido());
                    response.put("estado", actualizado.getEstado());
                    response.put("fecha", actualizado.getFecha());
                    response.put("direccionEnvio", actualizado.getDireccionEnvio());
                    response.put("distrito", actualizado.getDistrito());
                    response.put("esUrgente", actualizado.isEsUrgente());
                    response.put("latitudEntrega", actualizado.getLatitudEntrega());
                    response.put("longitudEntrega", actualizado.getLongitudEntrega());
                    response.put("latitudRepartidor", actualizado.getLatitudRepartidor());
                    response.put("longitudRepartidor", actualizado.getLongitudRepartidor());
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Actualizar las coordenadas GPS del repartidor desde la App o Panel de Delivery.
     */
    @PutMapping("/{id}/ubicacion-repartidor")
    public ResponseEntity<Void> actualizarUbicacionRepartidor(
            @PathVariable Integer id,
            @RequestBody UbicacionDTO ubicacion) {

        if (ubicacion == null || ubicacion.getLatitud() == null || ubicacion.getLongitud() == null) {
            return ResponseEntity.badRequest().build();
        }

        return pedidoRepository.findById(id).map(pedido -> {
            pedido.setLatitudRepartidor(BigDecimal.valueOf(ubicacion.getLatitud()));
            pedido.setLongitudRepartidor(BigDecimal.valueOf(ubicacion.getLongitud()));
            pedido.setUltimaActualizacionUbicacion(LocalDateTime.now());

            // Cambiar automáticamente el estado a EN_CAMINO si recién se activa la ubicación
            if ("PAGADO".equalsIgnoreCase(pedido.getEstado())) {
                pedido.setEstado("EN_CAMINO");
            }

            pedidoRepository.save(pedido);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Endpoint opcional para asignar un repartidor a la orden.
     */
    @PutMapping("/{id}/asignar-repartidor/{repartidorId}")
    public ResponseEntity<Void> asignarRepartidor(
            @PathVariable Integer id,
            @PathVariable Integer repartidorId) {

        return pedidoRepository.findById(id).map(pedido -> {
            pedido.setRepartidorId(repartidorId);
            pedidoRepository.save(pedido);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
