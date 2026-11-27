package com.example.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "detalle_pedido")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetallePedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer idDetallePedido;

    @ManyToOne
    @JoinColumn(name = "pedido_id", referencedColumnName = "id_pedido")
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;

    private Integer cantidad;

    @Column(name = "precio_historico")
    private BigDecimal precioHistorico;

    /** Presentación vendida (null si el producto se vendió sin presentaciones). */
    @ManyToOne
    @JoinColumn(name = "id_presentacion")
    private PresentacionProducto presentacion;

    /** Copia de la etiqueta al momento de la venta ("Blíster x10"): el historial no cambia si luego se edita. */
    @Column(name = "etiqueta_presentacion", length = 60)
    private String etiquetaPresentacion;

    /** Unidades base que descuenta cada unidad vendida (tabletas por blíster o caja). */
    @Column(name = "unidades_presentacion")
    private Integer unidadesPresentacion;
}
