package com.example.demo.dto.chatbot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import lombok.Getter;

/**
 * Producto en su versión mínima para mostrarse como tarjeta dentro del chat.
 *
 * Solo trae lo que el chat necesita (sin categoría, marca, descripción larga, etc.), por lo que
 * la consulta no carga las relaciones EAGER de la entidad Producto.
 *
 * El stock y la fecha de caducidad entran únicamente para calcular "agotado" y "maxPorPedido";
 * el stock real nunca se expone.
 */
@Getter
public class ProductoChatDTO {

    /** Límite de unidades de un mismo producto por carrito (el mismo que valida CarritoService). */
    public static final int LIMITE_POR_PRODUCTO = 10;

    private final Integer idProducto;
    private final String nombre;
    private final BigDecimal precioRegular;

    /** Precio de oferta; es null cuando el producto no está en oferta vigente. */
    private final BigDecimal precioOferta;

    private final boolean enOferta;

    /** Lo que realmente pagaría el cliente (oferta si existe, de lo contrario el precio regular). */
    private final BigDecimal precioFinal;

    /** Porcentaje de descuento redondeado (0 si no hay oferta). */
    private final int descuentoPorcentaje;

    private final boolean agotado;

    /** Máximo que el cliente puede pedir de una vez: el menor entre el stock y el límite por producto. */
    private final int maxPorPedido;

    private final String imgUrl;

    /** Constructor usado por la consulta JPQL "select new ...". El orden de los parámetros importa. */
    public ProductoChatDTO(Integer idProducto, String nombre, BigDecimal precioVenta, BigDecimal precioOferta,
                           Boolean enOferta, Integer stock, LocalDate fechaCaducidad, String imgUrl) {
        this.idProducto = idProducto;
        this.nombre = nombre;
        this.precioRegular = precioVenta;
        this.imgUrl = imgUrl;

        boolean ofertaVigente = Boolean.TRUE.equals(enOferta)
                && precioOferta != null
                && precioVenta != null
                && precioOferta.compareTo(precioVenta) < 0;

        this.enOferta = ofertaVigente;
        this.precioOferta = ofertaVigente ? precioOferta : null;
        this.precioFinal = ofertaVigente ? precioOferta : precioVenta;

        if (ofertaVigente && precioVenta.signum() > 0) {
            this.descuentoPorcentaje = precioVenta.subtract(precioOferta)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(precioVenta, 0, RoundingMode.HALF_UP)
                    .intValue();
        } else {
            this.descuentoPorcentaje = 0;
        }

        int unidades = stock == null ? 0 : Math.max(0, stock);
        boolean vencido = fechaCaducidad != null && fechaCaducidad.isBefore(LocalDate.now());
        this.agotado = unidades <= 0 || vencido;
        this.maxPorPedido = this.agotado ? 0 : Math.min(unidades, LIMITE_POR_PRODUCTO);
    }
}
