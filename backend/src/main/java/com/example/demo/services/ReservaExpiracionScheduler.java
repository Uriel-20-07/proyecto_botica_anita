package com.example.demo.services;

import com.example.demo.models.DetalleCarrito;
import com.example.demo.models.EstadoDetalleCarrito;
import com.example.demo.repositories.CarritoRepository;
import com.example.demo.repositories.DetalleCarritoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Libera las reservas de carrito que cumplieron 2 horas.
 *
 * No borra las líneas: las marca EXPIRADA para que el siguiente GET del
 * carrito las elimine y pueda avisar al usuario. Así el stock vuelve a estar
 * disponible aunque el usuario nunca vuelva a abrir el carrito.
 */
@Component
public class ReservaExpiracionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservaExpiracionScheduler.class);

    private final DetalleCarritoRepository detalleCarritoRepository;
    private final CarritoRepository carritoRepository;
    private final StockService stockService;

    public ReservaExpiracionScheduler(DetalleCarritoRepository detalleCarritoRepository,
                                      CarritoRepository carritoRepository,
                                      StockService stockService) {
        this.detalleCarritoRepository = detalleCarritoRepository;
        this.carritoRepository = carritoRepository;
        this.stockService = stockService;
    }

    @Scheduled(
            fixedDelayString = "${app.carrito.reserva.limpieza-ms:60000}",
            initialDelayString = "${app.carrito.reserva.limpieza-inicial-ms:15000}")
    @Transactional
    public void expirarReservas() {
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        List<DetalleCarrito> vencidas = detalleCarritoRepository
                .findByEstadoAndFechaExpiracionBefore(EstadoDetalleCarrito.ACTIVA, ahora);

        for (DetalleCarrito detalle : vencidas) {
            // Mismo orden de bloqueo que el checkout (carrito -> producto) para
            // evitar que una compra y el scheduler liberen stock a la vez.
            if (detalle.getCarrito() != null) {
                carritoRepository.findByIdForUpdate(detalle.getCarrito().getIdCarrito());
            }

            int actualizadas = detalleCarritoRepository.marcarExpiradaSiActiva(
                    detalle.getIdDetalleCarrito(), ahora,
                    EstadoDetalleCarrito.EXPIRADA, EstadoDetalleCarrito.ACTIVA);

            if (actualizadas > 0) {
                stockService.liberar(
                        detalle.getProducto().getIdProducto(),
                        detalle.getCantidad() != null ? detalle.getCantidad() : 0);
                log.info("Reserva expirada: producto {} ({} unidades) del carrito {}",
                        detalle.getProducto().getIdProducto(),
                        detalle.getCantidad(),
                        detalle.getCarrito() != null ? detalle.getCarrito().getIdCarrito() : "?");
            }
        }
    }
}
