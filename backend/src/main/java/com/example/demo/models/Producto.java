package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Formula;
import java.math.BigDecimal;

/**
 * Entidad JPA que representa un producto del catálogo de la farmacia.
 * 
 * Tabla en BD: "productos"
 * 
 * Cada producto pertenece a una categoría (relación ManyToOne) y
 * tiene un precio de venta, stock disponible y una imagen asociada.
 * 
 * @Data de Lombok genera: getters, setters, equals, hashCode y toString.
 */
@Entity
@Table(name = "productos")
@Data
public class Producto {

    /** Identificador único del producto (autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idProducto;

    /** Nombre del producto. Máximo 100 caracteres, obligatorio. */
    @Column(nullable = false, length = 100)
    private String nombre;

    /** Descripción detallada del producto (usos, presentación, etc.). */
    private String descripcion;

    /**
     * Precio de venta al público.
     * precision=10, scale=2: admite hasta 99,999,999.99 con 2 decimales.
     * Se usa BigDecimal para evitar errores de redondeo con double.
     */
    @Column(name = "precio_venta", precision = 10, scale = 2)
    private BigDecimal precioVenta;

    /**
     * Categoría a la que pertenece el producto (Analgésicos, Antibióticos, etc.).
     * Relación ManyToOne: muchos productos pueden pertenecer a una misma categoría.
     * JoinColumn define la columna de clave foránea en la tabla "productos".
     */
    @ManyToOne
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;

    /** URL de la imagen del producto (puede ser externa o ruta local). */
    @Column(name = "img_url", length = 500)
    private String imgUrl;

    /**
     * Precio de oferta del producto (precio rebajado).
     * Si es null, el producto no tiene precio especial de oferta.
     */
    @Column(name = "precio_oferta", precision = 10, scale = 2)
    private BigDecimal precioOferta;

    /**
     * Indica si el producto está actualmente en oferta.
     * Cuando es true, se usa precioOferta en lugar de precioVenta.
     */
    @Column(name = "en_oferta", nullable = false, columnDefinition = "boolean default false")
    private Boolean enOferta = false;

    /**
     * Unidades disponibles en inventario.
     * Stock mínimo = 0 (no puede ser negativo).
     * Valor por defecto: 0 (sin stock al crear el producto).
     */
    @Column(name = "stock", nullable = false, columnDefinition = "integer default 0")
    private Integer stock = 0;

    // Añade esto si no lo tienes en Producto.java
    @Column(name = "fecha_caducidad")
    private java.time.LocalDate fechaCaducidad;

    @Formula(
        "CASE " +
        "  WHEN en_oferta = true AND precio_oferta IS NOT NULL THEN precio_oferta " +
        "  ELSE precio_venta " +
        "END"
    )
    private BigDecimal precioConDescuento;

    @Formula(
        "CASE " +
        "  WHEN precio_venta IS NULL OR precio_venta = 0 THEN 0.00 " +
        "  WHEN en_oferta = true AND precio_oferta IS NOT NULL THEN (precio_venta - precio_oferta) / precio_venta " +
        "  ELSE 0.00 " +
        "END"
    )
    private BigDecimal descuentoPorcentaje;

    // Y asegúrate de tener sus respectivos Getters y Setters
    public java.time.LocalDate getFechaCaducidad() {
        return fechaCaducidad;
    }

    public void setFechaCaducidad(java.time.LocalDate fechaCaducidad) {
        this.fechaCaducidad = fechaCaducidad;
    }
}
