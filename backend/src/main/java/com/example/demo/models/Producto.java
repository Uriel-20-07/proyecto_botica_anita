package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Formula;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
    @JsonIgnoreProperties("subcategorias")
    private Categoria categoria;

    @ManyToOne
    @JoinColumn(name = "id_subcategoria")
    private Subcategoria subcategoria;

    @ManyToOne
    @JoinColumn(name = "id_marca")
    private Marca marca;

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

    /**
     * Unidades apartadas en carritos activos (reservas de 2 horas).
     * El stock disponible para vender es {@code stock - stockEnCarrito}.
     */
    @Column(name = "stock_en_carrito", nullable = false, columnDefinition = "integer default 0")
    private Integer stockEnCarrito = 0;

    // Añade esto si no lo tienes en Producto.java
    @Column(name = "fecha_caducidad")
    private java.time.LocalDate fechaCaducidad;

    // ─── Variantes (tallas) ────────────────────────────────────────────────────
    // Una "línea" de producto con varias tallas (pañales, ortopedia) se guarda como VARIOS productos, uno por talla,
    // cada uno con su propio stock, precio, lotes, carrito y pedidos. Estos campos solo los agrupan para mostrarlos
    // como una sola tarjeta en el catálogo y con un selector de talla en el detalle.

    /** Clave de la línea (ej. "huggies-active-sec"). Null = producto sin variantes. */
    @Column(name = "grupo_variantes", length = 60)
    private String grupoVariantes;

    /** Nombre de la línea completa, para la tarjeta del catálogo (ej. "Pañales Huggies Active Sec"). */
    @Column(name = "grupo_nombre", length = 120)
    private String grupoNombre;

    /** Talla de este producto dentro de su línea (ej. "G", "XG", "RN"). */
    @Column(name = "variante_etiqueta", length = 30)
    private String varianteEtiqueta;

    /** Orden de la talla dentro de la línea (de menor a mayor). */
    @Column(name = "variante_orden", nullable = false, columnDefinition = "integer default 0")
    private Integer varianteOrden = 0;

    /**
     * true = este producto da la cara por su línea en el catálogo. Los productos sin variantes siempre son true;
     * en una línea con tallas solo UNA es true y las demás no aparecen como tarjetas aparte.
     */
    @Column(name = "es_representante", nullable = false, columnDefinition = "boolean default true")
    private Boolean esRepresentante = true;

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

    /** Agotado físico = sin stock O vencido. Jackson lo expone en el JSON como "agotado". */
    public boolean isAgotado() {
        boolean sinStock = getStock() == null || getStock() <= 0;
        return sinStock || estaVencido();
    }

    /**
     * Unidades que realmente se pueden vender ahora:
     * stock físico menos lo que está reservado en carritos.
     */
    public Integer getStockDisponible() {
        int fisico = getStock() != null ? getStock() : 0;
        int enCarrito = getStockEnCarrito() != null ? getStockEnCarrito() : 0;
        return Math.max(0, fisico - enCarrito);
    }

    /**
     * Agotado para la venta online: no queda disponible o el producto venció.
     * No confundir con {@link #isAgotado()}, que representa el stock físico.
     */
    public boolean isAgotadoOnline() {
        return getStockDisponible() <= 0 || estaVencido();
    }

    /** true si hay al menos una unidad apartada en carritos. */
    public boolean isReservado() {
        return getStockEnCarrito() != null && getStockEnCarrito() > 0;
    }

    /**
     * Estado derivado para mostrar en catálogo/panel:
     * VENCIDO, AGOTADO, RESERVADO, PARCIALMENTE_RESERVADO o DISPONIBLE.
     */
    public String getEstadoStock() {
        if (estaVencido()) {
            return "VENCIDO";
        }
        int fisico = getStock() != null ? getStock() : 0;
        int enCarrito = getStockEnCarrito() != null ? getStockEnCarrito() : 0;
        int disponible = Math.max(0, fisico - enCarrito);
        if (fisico <= 0) {
            return "AGOTADO";
        }
        if (disponible <= 0) {
            return "RESERVADO";
        }
        if (enCarrito > 0) {
            return "PARCIALMENTE_RESERVADO";
        }
        return "DISPONIBLE";
    }

    private boolean estaVencido() {
        return getFechaCaducidad() != null && getFechaCaducidad().isBefore(java.time.LocalDate.now());
    }
}
