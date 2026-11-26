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

    public VentaPresencialService(ProductoRepository productoRepository,
                                  PedidoRepository pedidoRepository,
                                  DetallePedidoRepository detallePedidoRepository,
                                  StockService stockService,
                                  EmailService emailService) {
        this.productoRepository = productoRepository;
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.stockService = stockService;
        this.emailService = emailService;
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

            if (producto.getStockDisponible() < cantidad) {
                throw new RuntimeException("Stock disponible insuficiente para " + producto.getNombre()
                        + ". Disponible: " + producto.getStockDisponible() + " unidad(es).");
            }

            double precio = producto.getPrecioVenta() != null ? producto.getPrecioVenta().doubleValue() : 0.0;
            total += precio * cantidad;

            DetallePedido detalle = new DetallePedido();
            detalle.setPedido(pedido);
            detalle.setProducto(producto);
            detalle.setCantidad(cantidad);
            detalle.setPrecioHistorico(producto.getPrecioVenta());
            detalles.add(detalle);
        }

        pedido.setTotal(BigDecimal.valueOf(total));
        pedidoRepository.save(pedido);

        // Guardar detalles y descontar stock físico (sin tocar reservas online).
        for (DetallePedido detalle : detalles) {
            detallePedidoRepository.save(detalle);
            stockService.venderSinReserva(detalle.getProducto().getIdProducto(), detalle.getCantidad());
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
}
