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

    // Tienda Botica Anita: esquina Las Violetas con Av. Los Jazmines 408, Independencia.
    private static final double TIENDA_LAT = -12.0016;
    private static final double TIENDA_LNG = -77.0501;
    // Minutos fijos por etapa antes del viaje (igual que el frontend).
    private static final long MIN_PREVIOS = 1 + 5 + 5;

    /**
     * Consultar el estado y ubicación del pedido en tiempo real para el cliente.
     * Avanza el estado solo con el paso del tiempo (1+5+5 min + viaje + 1).
     */
    @GetMapping("/{id}/tracking")
public ResponseEntity<?> obtenerTracking(@PathVariable Integer id) {
    return pedidoRepository.findById(id)
        .<ResponseEntity<?>>map(pedido -> {
            avanzarEstadoPorTiempo(pedido);
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
     * Avanza PAGADO/CONFIRMADO -> EN_CAMINO (11 min) -> COMPLETADO (11 + viaje + 1).
     * El viaje se calcula a 25 km/h; sin coordenadas usa 5 min por defecto.
     */
    private void avanzarEstadoPorTiempo(Pedido pedido) {
        if (pedido.getFecha() == null) return;
        String estado = pedido.getEstado() != null ? pedido.getEstado().toUpperCase() : "";
        if (estado.contains("COMPLETAD") || estado.contains("ENTREGAD")
                || estado.contains("CANCELAD") || estado.contains("RECHAZAD")) {
            return;
        }
        long minutos = java.time.Duration.between(pedido.getFecha(), LocalDateTime.now()).toMinutes();
        double tripMin = 5.0;
        if (pedido.getLatitudEntrega() != null && pedido.getLongitudEntrega() != null) {
            double distM = distanciaM(TIENDA_LAT, TIENDA_LNG,
                    pedido.getLatitudEntrega().doubleValue(), pedido.getLongitudEntrega().doubleValue());
            tripMin = Math.max(1.0, (distM / 1000.0 / 25.0) * 60.0);
        }
        boolean cambio = false;
        if ((estado.equals("PAGADO") || estado.equals("CONFIRMADO")) && minutos >= MIN_PREVIOS) {
            pedido.setEstado("EN_CAMINO");
            cambio = true;
        }
        if ("EN_CAMINO".equalsIgnoreCase(pedido.getEstado()) && minutos >= MIN_PREVIOS + tripMin + 1) {
            pedido.setEstado("COMPLETADO");
            cambio = true;
        }
        if (cambio) {
            pedidoRepository.save(pedido);
        }
    }

    /** Distancia en metros (Haversine). */
    private double distanciaM(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
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