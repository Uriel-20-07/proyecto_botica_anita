package com.example.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un cupón de descuento.
 * 
 * Tabla en BD: "cupones"
 * 
 * Los cupones se generan automáticamente al registrarse un nuevo usuario
 * (cupón de bienvenida con 30% de descuento). El tipo de descuento es
 * "PORCENTAJE" y el valor indica el porcentaje a descontar.
 * 
 * Un cupón es de un solo uso (usado = true después de aplicarse)
 * y tiene una fecha de expiración configurada.
 */
@Entity
@Table(name = "cupones")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cupon {

    /** Identificador único del cupón (autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cupon")
    private Integer id;

    /**
     * Código alfanumérico único del cupón (ej: "BIENVENIDA-ABCD123456").
     * El usuario ingresa este código en el checkout para aplicar el descuento.
     */
    @Column(nullable = false, unique = true)
    private String codigo;

    /**
     * Usuario al que pertenece este cupón.
     * ManyToOne: un usuario puede tener múltiples cupones.
     */
    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private User usuario;

    /**
     * Tipo de descuento aplicado. Valores posibles:
     * - "PORCENTAJE": descuenta un % del subtotal.
     * - "MONTO_FIJO": descuenta un monto fijo (no implementado aún).
     */
    @Column(name = "tipo_descuento", nullable = false)
    private String tipoDescuento;

    /**
     * Valor del descuento.
     * Si tipoDescuento = "PORCENTAJE", este valor es el porcentaje (ej: 30 = 30%).
     */
    @Column(name = "valor_descuento", nullable = false)
    private BigDecimal valorDescuento;

    /** Fecha y hora en que se creó el cupón. */
    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    /** Fecha y hora límite para usar el cupón. Después de esta fecha no es válido. */
    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    /** Indica si el cupón ya fue utilizado. Un cupón solo puede usarse una vez. */
    @Column(nullable = false)
    private Boolean usado = false;

    /** Fecha y hora en que se utilizó el cupón (null si no se ha usado aún). */
    @Column(name = "fecha_uso")
    private LocalDateTime fechaUso;

    /** ID del pedido en que se utilizó el cupón (referencia opcional). */
    @Column(name = "id_pedido")
    private Integer idPedido;

    /** Descripción legible del cupón (ej: "Cupón de bienvenida 30% descuento"). */
    @Column(nullable = false)
    private String descripcion;

    /** Indica si el cupón está activo. Un cupón inactivo no puede ser utilizado. */
    @Column(nullable = false)
    private Boolean activo = true;
}
