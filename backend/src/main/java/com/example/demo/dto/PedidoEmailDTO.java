package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.models.DetallePedido;
import com.example.demo.models.Pedido;
import com.example.demo.models.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos planos de un pedido para enviar correos de notificacion de forma
 * asincrona.
 *
 * IMPORTANTE: debe construirse dentro de la transaccion principal, mientras
 * las relaciones JPA (usuario, producto, etc.) siguen disponibles. El hilo
 * asincrono solo recibe este DTO y nunca toca entidades.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoEmailDTO {

    private String destinatario;
    private String nombreCliente;
    private String numeroPedido;
    private LocalDateTime fecha;
    private String direccionEnvio;
    private boolean esUrgente;
    private String distrito;
    private String metodoPago;
    private BigDecimal total;
    private String codigoCupon;
    private BigDecimal latitudEntrega;
    private BigDecimal longitudEntrega;
    private List<ItemPedidoEmailDTO> items;
    private String numeroOperacion;
    private String estado;

    /**
     * Construye el DTO a partir de un usuario registrado. Debe invocarse
     * dentro de la transaccion.
     */
    public static PedidoEmailDTO from(Pedido pedido, User usuario, List<DetallePedido> detalles,
                                      String codigoCupon, String numeroPedido) {
        String destinatario = usuario != null ? usuario.getEmail() : null;

        StringBuilder nombre = new StringBuilder();
        if (usuario != null) {
            if (usuario.getNombre() != null && !usuario.getNombre().isBlank()) {
                nombre.append(usuario.getNombre().trim());
            }
            if (usuario.getApellido() != null && !usuario.getApellido().isBlank()) {
                if (nombre.length() > 0) {
                    nombre.append(' ');
                }
                nombre.append(usuario.getApellido().trim());
            }
        }

        return from(pedido, destinatario, nombre.toString(), detalles, codigoCupon, numeroPedido);
    }

    /**
     * Construye el DTO con destinatario y nombre ya resueltos (util para la
     * venta presencial, donde no siempre existe un {@link User}).
     */
    public static PedidoEmailDTO from(Pedido pedido, String destinatario, String nombreCliente,
                                      List<DetallePedido> detalles, String codigoCupon,
                                      String numeroPedido) {
        List<ItemPedidoEmailDTO> items = new ArrayList<>();
        if (detalles != null) {
            for (DetallePedido det : detalles) {
                if (det == null) {
                    continue;
                }
                items.add(ItemPedidoEmailDTO.builder()
                        .nombre(det.getProducto() != null ? det.getProducto().getNombre() : "Producto")
                        .presentacion(det.getEtiquetaPresentacion())
                        .cantidad(det.getCantidad() != null ? det.getCantidad() : 0)
                        .precioUnitario(det.getPrecioHistorico())
                        .build());
            }
        }

        return PedidoEmailDTO.builder()
                .destinatario(destinatario)
                .nombreCliente(nombreCliente != null ? nombreCliente : "")
                .numeroPedido(numeroPedido)
                .fecha(pedido != null ? pedido.getFecha() : null)
                .direccionEnvio(pedido != null ? pedido.getDireccionEnvio() : null)
                .esUrgente(pedido != null && pedido.isEsUrgente())
                .distrito(pedido != null ? pedido.getDistrito() : null)
                .metodoPago(pedido != null ? pedido.getMetodoPago() : null)
                .total(pedido != null ? pedido.getTotal() : null)
                .codigoCupon(codigoCupon)
                .latitudEntrega(pedido != null ? pedido.getLatitudEntrega() : null)
                .longitudEntrega(pedido != null ? pedido.getLongitudEntrega() : null)
                .items(items)
                .numeroOperacion(pedido != null ? pedido.getNumeroOperacion() : null)
                .estado(pedido != null && pedido.getEstado() != null ? pedido.getEstado().toString() : null)
                .build();
    }

    public String getNumeroOperacion() {
        return numeroOperacion;
    }

    public void setNumeroOperacion(String numeroOperacion) {
        this.numeroOperacion = numeroOperacion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}