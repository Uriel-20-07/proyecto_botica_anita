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

import com.example.demo.dto.TrackingResponseDTO;
import com.example.demo.dto.UbicacionDTO;
import com.example.demo.models.Pedido;
import com.example.demo.repositories.PedidoRepository;

@RestController
@RequestMapping("/api/pedidos-tracking")
// CORS: gobernado por la configuración global en SecurityConfig (incluye
// localhost y el dominio de producción). No se restringe aquí para evitar
// que este controlador quede bloqueado en producción.
public class PedidoTrackingController {

    private final PedidoRepository pedidoRepository;

    public PedidoTrackingController(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    /**
     * Consultar el estado y ubicación del pedido en tiempo real para el cliente.
     */
    @GetMapping("/{id}/tracking")
public ResponseEntity<?> obtenerTracking(@PathVariable Integer id) {
    return pedidoRepository.findById(id)
        .<ResponseEntity<?>>map(pedido -> {
            Map<String, Object> response = new HashMap<>();
            response.put("idPedido", pedido.getIdPedido());
            response.put("estado", pedido.getEstado());
            response.put("direccionEnvio", pedido.getDireccionEnvio());
            response.put("distrito", pedido.getDistrito());
            response.put("latitudEntrega", pedido.getLatitudEntrega());
            response.put("longitudEntrega", pedido.getLongitudEntrega());
            response.put("latitudRepartidor", pedido.getLatitudRepartidor());
            response.put("longitudRepartidor", pedido.getLongitudRepartidor());
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

    // Mapper privado para transformar Pedido a TrackingResponseDTO
    private TrackingResponseDTO mapToTrackingResponse(Pedido pedido) {
        return TrackingResponseDTO.builder()
                .idPedido(pedido.getIdPedido())
                .estado(pedido.getEstado())
                .direccionEnvio(pedido.getDireccionEnvio())
                .distrito(pedido.getDistrito())
                .esUrgente(pedido.isEsUrgente())
                .latitudEntrega(pedido.getLatitudEntrega())
                .longitudEntrega(pedido.getLongitudEntrega())
                .repartidorId(pedido.getRepartidorId())
                .latitudRepartidor(pedido.getLatitudRepartidor())
                .longitudRepartidor(pedido.getLongitudRepartidor())
                .ultimaActualizacionUbicacion(pedido.getUltimaActualizacionUbicacion())
                .build();
    }
}