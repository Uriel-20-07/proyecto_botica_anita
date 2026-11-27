package com.example.demo.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.PedidoEmailDTO;
import com.example.demo.models.DetallePedido;
import com.example.demo.models.Pedido;
import com.example.demo.models.PresentacionProducto;
import com.example.demo.models.Producto;
import com.example.demo.repositories.DetallePedidoRepository;
import com.example.demo.repositories.PedidoRepository;
import com.example.demo.repositories.ProductoRepository;

/**
 * Registra una venta presencial (POS) en una sola transacción.
 *
 * El POS solo puede vender stock disponible: nunca las unidades que están
 * reservadas por carritos online.
 */
@Service
public class VentaPresencialService {

    private final ProductoRepository productoRepository;
    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final StockService stockService;
    private final EmailService emailService;
    private final PresentacionService presentacionService;

    public VentaPresencialService(ProductoRepository productoRepository,
                                  PedidoRepository pedidoRepository,
                                  DetallePedidoRepository detallePedidoRepository,
                                  StockService stockService,
                                  EmailService emailService,
                                  PresentacionService presentacionService) {
        this.productoRepository = productoRepository;
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.stockService = stockService;
        this.emailService = emailService;
        this.presentacionService = presentacionService;
    }

    @Transactional
    public Map<String, Object> registrarVenta(List<Map<String, Object>> items,
                                              String metodoPago,
                                              String emailCliente,
                                              String nombreCliente) {
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("El carrito está vacío.");
        }

        Pedido pedido = new Pedido();
        pedido.setFecha(LocalDateTime.now(ZoneOffset.UTC));
        pedido.setEstado("PAGADO");
        pedido.setMetodoPago(metodoPago);
        pedido.setEsUrgente(false);
        pedido.setDireccionEnvio("Venta en tienda");
        pedido.setDistrito("Independencia");

        double total = 0.0;
        List<DetallePedido> detalles = new ArrayList<>();

        for (Map<String, Object> item : items) {
            Integer idProd = Integer.valueOf(String.valueOf(item.get("idProducto")));
            int cantidad = Integer.parseInt(String.valueOf(item.get("cantidad")));

            Producto producto = productoRepository.findById(idProd)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + idProd));

            // Con presentaciones (blíster, caja) el stock está en unidades base: 2 cajas de 100 = 200 tabletas.
            PresentacionProducto presentacion = presentacionService.resolver(producto, idPresentacionDe(item));
            int unidadesNecesarias = cantidad * presentacionService.unidadesDe(presentacion);

            if (producto.getStockDisponible() < unidadesNecesarias) {
                throw new RuntimeException("Stock disponible insuficiente para " + producto.getNombre()
                        + ". Disponible: " + producto.getStockDisponible() + " unidad(es).");
            }

            // La venta en tienda usa el precio regular (sin ofertas), como siempre; con presentación, el de ella.
            BigDecimal precioRegular = presentacionService.precioRegular(producto, presentacion);
            double precio = precioRegular != null ? precioRegular.doubleValue() : 0.0;
            total += precio * cantidad;

            DetallePedido detalle = new DetallePedido();
            detalle.setPedido(pedido);
            detalle.setProducto(producto);
            detalle.setCantidad(cantidad);
            detalle.setPrecioHistorico(precioRegular);
            if (presentacion != null) {
                detalle.setPresentacion(presentacion);
                detalle.setEtiquetaPresentacion(presentacion.getEtiqueta());
                detalle.setUnidadesPresentacion(presentacionService.unidadesDe(presentacion));
            }
            detalles.add(detalle);
        }

        pedido.setTotal(BigDecimal.valueOf(total));
        pedidoRepository.save(pedido);

        // Guardar detalles y descontar stock físico (sin tocar reservas online).
        for (DetallePedido detalle : detalles) {
            detallePedidoRepository.save(detalle);
            int unidadesVendidas = detalle.getCantidad()
                    * (detalle.getUnidadesPresentacion() != null ? detalle.getUnidadesPresentacion() : 1);
            stockService.venderSinReserva(detalle.getProducto().getIdProducto(), unidadesVendidas);
        }

        // La boleta sale después del commit para no enviar correos de ventas revertidas.
        if (emailCliente != null && !emailCliente.isBlank()) {
            String nro = String.format("%06d", pedido.getIdPedido());
            PedidoEmailDTO emailDto = PedidoEmailDTO.from(
                    pedido, emailCliente, nombreCliente, detalles, null, nro);
            AfterCommitRunner.run(() -> emailService.sendBoletaPresencialEmail(emailDto));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("idPedido", pedido.getIdPedido());
        response.put("total", pedido.getTotal());
        response.put("nroBoleta", String.format("%06d", pedido.getIdPedido()));
        return response;
    }

    /** Presentación elegida en un ítem de venta (opcional): null si no viene o viene vacía. */
    private static Integer idPresentacionDe(Map<String, Object> item) {
        Object valor = item.get("idPresentacion");
        if (valor == null || String.valueOf(valor).isBlank() || "null".equals(String.valueOf(valor))) {
            return null;
        }
        return Integer.valueOf(String.valueOf(valor));
    }
}
