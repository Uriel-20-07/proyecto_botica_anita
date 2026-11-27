package com.example.demo.models;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Una forma de vender un producto cuyo stock se comparte: por ejemplo "Blíster x10" y "Caja x100" de una misma
 * pastilla.
 *
 * El stock del producto se cuenta en la unidad base (tabletas): vender 2 cajas de 100 descuenta 200 tabletas.
 * Un producto sin presentaciones se vende como siempre (una unidad por venta).
 *
 * - precioVenta == null: usa el precio del producto (es el caso de la presentación predeterminada, la caja), de
 *   modo que nunca se desfasa si cambias el precio del producto.
 * - esPredeterminada: la presentación que se usa cuando el cliente (o un sistema antiguo) no elige ninguna.
 * - activo: en lugar de borrar una presentación, desactívala (los pedidos antiguos la siguen referenciando).
 *
 * Ver docs/presentaciones_01_migracion.sql.
 */
@Entity
@Table(name = "presentaciones_producto")
public class PresentacionProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_presentacion")
    private Integer idPresentacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    @JsonIgnore
    private Producto producto;

    @Column(nullable = false, length = 60)
    private String etiqueta;

    /** Unidades base (tabletas, pañales...) que descuenta una unidad de esta presentación. */
    @Column(nullable = false)
    private Integer unidades;

    @Column(name = "precio_venta", precision = 10, scale = 2)
    private BigDecimal precioVenta;

    @Column(name = "es_predeterminada", nullable = false)
    private boolean esPredeterminada;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    private Integer orden = 0;

    public Integer getIdPresentacion() { return idPresentacion; }
    public void setIdPresentacion(Integer idPresentacion) { this.idPresentacion = idPresentacion; }

    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }

    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }

    public Integer getUnidades() { return unidades; }
    public void setUnidades(Integer unidades) { this.unidades = unidades; }

    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }

    public boolean isEsPredeterminada() { return esPredeterminada; }
    public void setEsPredeterminada(boolean esPredeterminada) { this.esPredeterminada = esPredeterminada; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }
}
