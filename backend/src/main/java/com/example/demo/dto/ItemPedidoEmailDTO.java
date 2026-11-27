package com.example.demo.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Linea de producto ya "aplanada" para los correos de notificacion.
 * No contiene entidades JPA, por lo que puede usarse en otro hilo sin riesgo
 * de LazyInitializationException.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemPedidoEmailDTO {

    private String nombre;
    /** Etiqueta de la presentación vendida ("Blíster x10"); null si el producto no tiene presentaciones. */
    private String presentacion;
    private int cantidad;
    private BigDecimal precioUnitario;
}
