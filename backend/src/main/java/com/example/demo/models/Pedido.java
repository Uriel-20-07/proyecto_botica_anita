package com.example.demo.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad JPA que representa un pedido (orden de compra) de un usuario.
 * 
 * Tabla en BD: "pedidos"
 * 
 * Un pedido es la cabecera de una compra completada. Los productos
 * específicos que se compraron se almacenan en DetallePedido.
 * 
 * Estados posibles:
 * - "PENDIENTE": registrado pero no pagado/procesado.
 * - "PAGADO": pago procesado exitosamente.
 * - "EN_CAMINO": repartidor en ruta de entrega.
 * - "COMPLETADO": pedido entregado.
 * - "CANCELADO": pedido anulado.
 */
@Entity
@Table(name = "pedidos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {

    /** Identificador único del pedido (autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Integer idPedido;

    /**
     * Usuario que realizó el pedido.
     * ManyToOne: un usuario puede tener múltiples pedidos.
     * La FK "id_usuario" referencia la PK "id_usuario" de la tabla "usuarios".
     */
    @ManyToOne
    @JoinColumn(name = "id_usuario", referencedColumnName = "id_usuario")
    private User usuario;

    /** Fecha y hora en que se realizó el pedido. */
    private LocalDateTime fecha;

    /** Estado actual del pedido ("PENDIENTE", "PAGADO", "EN_CAMINO", "COMPLETADO", etc.). */
    private String estado;

    /**
     * Total monetario del pedido (puede incluir descuentos por cupón).
     * Se usa BigDecimal para evitar errores de precisión con montos monetarios.
     */
    private BigDecimal total;

    /** Dirección o establecimiento de recojo del pedido. */
    @Column(name = "direccion_envio", nullable = true)
    private String direccionEnvio;

    /** Indica si el pedido es urgente (envío a domicilio por S/ 10). */
    @Column(name = "es_urgente", nullable = false, columnDefinition = "boolean default false")
    private boolean esUrgente = false;

    /** Distrito de entrega o recojo. */
    @Column(name = "distrito", nullable = true)
    private String distrito;

    /** Método de pago utilizado. */
    @Column(name = "metodo_pago", nullable = true)
    private String metodoPago;

    /** ID de la receta médica asociada (opcional, null si no requiere). */
    @Column(name = "id_receta", nullable = true)
    private Integer idReceta;

    // ==========================================
    // CAMPOS PARA SEGUIMIENTO Y GEOLOCALIZACIÓN
    // ==========================================

    /** Latitud de la dirección de entrega del cliente. */
    @Column(name = "latitud_entrega", precision = 10, scale = 8)
    private BigDecimal latitudEntrega;

    /** Longitud de la dirección de entrega del cliente. */
    @Column(name = "longitud_entrega", precision = 11, scale = 8)
    private BigDecimal longitudEntrega;

    /** ID del repartidor asignado al pedido. */
    @Column(name = "repartidor_id")
    private Integer repartidorId;

    /** Latitud en tiempo real del repartidor. */
    @Column(name = "latitud_repartidor", precision = 10, scale = 8)
    private BigDecimal latitudRepartidor;

    /** Longitud en tiempo real del repartidor. */
    @Column(name = "longitud_repartidor", precision = 11, scale = 8)
    private BigDecimal longitudRepartidor;

    /** Última fecha y hora de actualización del GPS del repartidor. */
    @Column(name = "ultima_actualizacion_ubicacion")
    private LocalDateTime ultimaActualizacionUbicacion;

        /** Número de operación del pago por Plin (null para otros métodos). */
    @Column(name = "numero_operacion", nullable = true, length = 20)
    private String numeroOperacion;
}
