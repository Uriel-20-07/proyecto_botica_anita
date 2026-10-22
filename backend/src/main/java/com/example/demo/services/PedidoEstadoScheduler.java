package com.example.demo.services;

import com.example.demo.models.Pedido;
import com.example.demo.repositories.PedidoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Avanza automaticamente el estado de los pedidos que siguen en curso.
 *
 * Sin esto, un pedido solo cambiaba de estado cuando alguien abria la pantalla
 * de seguimiento (que es lo que llama al endpoint de tracking).
 *
 * El scheduler NO geocodifica: usa el tiempo de viaje por defecto. La
 * geocodificacion la hace el endpoint de tracking, que es donde el cliente
 * esta mirando el mapa. Asi no se satura Nominatim (responde 429 si no).
 */
@Component
public class PedidoEstadoScheduler {

    /** Pedidos con mas de 2 horas se dejan como están (son pruebas antiguas). */
    private static final long MAX_ANTIGUEDAD_MINUTOS = 120;

    private final PedidoRepository pedidoRepository;
    private final PedidoTrackingService trackingService;

    public PedidoEstadoScheduler(PedidoRepository pedidoRepository, PedidoTrackingService trackingService) {
        this.pedidoRepository = pedidoRepository;
        this.trackingService = trackingService;
    }

    /** Revisa los pedidos activos cada 30 segundos. */
    @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    public void actualizarEstados() {
        List<Pedido> pedidos = pedidoRepository.findAll();
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        for (Pedido pedido : pedidos) {
            String estado = pedido.getEstado() != null ? pedido.getEstado().toUpperCase() : "";
            boolean activo = estado.equals("PAGADO") || estado.equals("CONFIRMADO")
                    || estado.equals("EN_CAMINO") || estado.equals("LISTO_RECOJO");
            if (!activo) continue;
            if (pedido.getFecha() == null) continue;
            long antiguedadMin = Duration.between(pedido.getFecha(), ahora).toMinutes();
            if (antiguedadMin > MAX_ANTIGUEDAD_MINUTOS) continue;
            try {
                // Sin geocodificar: el tiempo de viaje por defecto es suficiente
                // para avanzar el estado sin pegarle a Nominatim.
                trackingService.avanzarEstado(pedido, false);
            } catch (Exception e) {
                // Un pedido con problemas no debe detener el resto.
                System.out.println("No se pudo avanzar el estado del pedido "
                        + pedido.getIdPedido() + ": " + e.getMessage());
            }
        }
    }
}
